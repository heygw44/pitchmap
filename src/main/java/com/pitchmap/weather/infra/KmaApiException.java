package com.pitchmap.weather.infra;

/**
 * 기상청 API 호출이 실패했을 때 클라이언트가 던진다.
 *
 * <p>요청 주소에는 인증키가 쿼리 값으로 들어간다. 그래서 클라이언트는 이 예외의 메시지에 주소나 키를 넣지 않고, 작업 이름, HTTP 상태, 원천
 * 오류 코드만 넣는다.
 */
public class KmaApiException extends RuntimeException {

    private final boolean retryable;

    public KmaApiException(String message) {
        this(message, null, false);
    }

    public KmaApiException(String message, Throwable cause) {
        this(message, cause, false);
    }

    private KmaApiException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    /** 다시 보내면 성공할 수 있는 실패(I/O 오류, 타임아웃, HTTP 5xx)일 때 만든다. */
    static KmaApiException retryable(String message) {
        return new KmaApiException(message, null, true);
    }

    static KmaApiException retryable(String message, Throwable cause) {
        return new KmaApiException(message, cause, true);
    }

    boolean isRetryable() {
        return retryable;
    }
}
