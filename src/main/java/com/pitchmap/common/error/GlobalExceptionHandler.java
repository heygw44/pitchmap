package com.pitchmap.common.error;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (e instanceof InvalidFieldException invalidField) {
            log.debug("invalid field field={} message={}", invalidField.getField(), e.getMessage());
            return ResponseEntity.status(errorCode.httpStatus())
                    .body(ErrorResponse.ofInvalidInput(
                            List.of(new ErrorResponse.FieldError(invalidField.getField(), e.getMessage()))));
        }
        if (errorCode.httpStatus().is5xxServerError()) {
            log.error("business exception code={}", errorCode.name(), e);
        } else {
            log.debug("business exception code={} message={}", errorCode.name(), e.getMessage());
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(errorCode.httpStatus());
        if (e instanceof RateLimitedException rateLimited) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds(rateLimited.retryAfter())));
        }
        return response.body(ErrorResponse.of(errorCode, e.getMessage(), e.extraFields()));
    }

    // Retry-After는 정수 초만 받는다. 그래서 소수 초는 올리고, 0초 이하는 1초로 맞춰 클라이언트가 바로 재시도하지 않게 한다.
    private static long retryAfterSeconds(Duration retryAfter) {
        long seconds = retryAfter.getSeconds() + (retryAfter.getNano() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }

    // 여기가 마지막 방어선이다. 예상하지 못한 예외가 와도 서버는 스택 트레이스를 로그에 남기고, 원인은 응답에 담지 않는다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
        log.error("unexpected exception", e);
        return internalError();
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            // 형 변환에 실패하면 Spring 기본 메시지가 java.lang.Integer 같은 자바 타입 이름이 든 영어 문장이라서, 개발자가 정한 문구로 바꿔 내보낸다.
            String message = fieldError.isBindingFailure() ? "값의 형식이 올바르지 않습니다." : fieldError.getDefaultMessage();
            fieldErrors.add(new ErrorResponse.FieldError(fieldError.getField(), message));
        }
        for (ObjectError globalError : ex.getBindingResult().getGlobalErrors()) {
            fieldErrors.add(new ErrorResponse.FieldError(globalError.getObjectName(), globalError.getDefaultMessage()));
        }
        return invalidInput(headers, status, fieldErrors);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            addFieldErrors(fieldErrors, result);
        }
        return invalidInput(headers, status, fieldErrors);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return invalidInput(
                headers, status, List.of(new ErrorResponse.FieldError(ex.getParameterName(), ex.getMessage())));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            return invalidInput(
                    headers, status, List.of(new ErrorResponse.FieldError(mismatch.getName(), "값의 형식이 올바르지 않습니다.")));
        }
        return invalidInput(headers, status, List.of());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            log.error("framework exception status={}", statusCode.value(), ex);
            return ResponseEntity.status(statusCode)
                    .headers(headers)
                    .body(ErrorResponse.of(CommonErrorCode.INTERNAL_ERROR));
        }
        return ResponseEntity.status(statusCode).headers(headers).body(ErrorResponse.of(clientErrorCode(statusCode)));
    }

    private static ErrorCode clientErrorCode(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 404 -> CommonErrorCode.NOT_FOUND;
            case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
            case 415 -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> CommonErrorCode.INVALID_INPUT;
        };
    }

    private static void addFieldErrors(List<ErrorResponse.FieldError> fieldErrors, ParameterValidationResult result) {
        String parameterName = parameterName(result.getMethodParameter());
        if (result instanceof ParameterErrors parameterErrors) {
            for (FieldError fieldError : parameterErrors.getFieldErrors()) {
                fieldErrors.add(new ErrorResponse.FieldError(fieldError.getField(), fieldError.getDefaultMessage()));
            }
            for (ObjectError globalError : parameterErrors.getGlobalErrors()) {
                fieldErrors.add(new ErrorResponse.FieldError(parameterName, globalError.getDefaultMessage()));
            }
            return;
        }
        for (MessageSourceResolvable error : result.getResolvableErrors()) {
            fieldErrors.add(new ErrorResponse.FieldError(parameterName, error.getDefaultMessage()));
        }
    }

    private static String parameterName(MethodParameter parameter) {
        String name = parameter.getParameterName();
        if (name != null) {
            return name;
        }
        return "arg" + parameter.getParameterIndex();
    }

    private ResponseEntity<Object> invalidInput(
            HttpHeaders headers, HttpStatusCode status, List<ErrorResponse.FieldError> fieldErrors) {
        return ResponseEntity.status(status).headers(headers).body(ErrorResponse.ofInvalidInput(fieldErrors));
    }

    private static ResponseEntity<ErrorResponse> internalError() {
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ErrorResponse.of(CommonErrorCode.INTERNAL_ERROR));
    }
}
