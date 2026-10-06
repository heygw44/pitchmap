package com.pitchmap.common.mail;

/**
 * 메일을 보내지 못했을 때 던진다.
 *
 * <p>호출하는 쪽(예: 아웃박스 발행기)이 예외 메시지를 DB에 기록하므로, 메시지에는 수신자 주소나 본문을 넣지 않는다.
 * 원인 예외(cause)는 SMTP 서버의 응답 문장을 담고 있어 수신자 주소가 들어 있을 수 있다.
 * 그래서 원인은 이 예외에 연결만 해 두고, 로그에 원인의 메시지를 찍지 않는다.
 */
public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException(String message) {
        super(message);
    }

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
