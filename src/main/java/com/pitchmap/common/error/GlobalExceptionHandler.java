package com.pitchmap.common.error;

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
        if (errorCode.httpStatus().is5xxServerError()) {
            log.error("business exception code={}", errorCode.name(), e);
        } else {
            log.debug("business exception code={} message={}", errorCode.name(), e.getMessage());
        }
        return ResponseEntity.status(errorCode.httpStatus()).body(ErrorResponse.of(errorCode, e.getMessage()));
    }

    // 마지막 방어선: 어떤 예외도 스택 트레이스가 로그에 남고 원인은 응답에 새지 않아야 한다
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
            fieldErrors.add(new ErrorResponse.FieldError(fieldError.getField(), fieldError.getDefaultMessage()));
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
