package com.pitchmap.member.application;

/**
 * 인증 코드 발송 요청 이벤트의 종류와 내용. 가입과 재발송이 기록하고 발송 처리기가 읽으므로 이름과 형태를 한 곳에 둔다.
 * 이벤트에는 코드 원값을 싣지 않는다. 처리기가 메일을 보내기 직전에 코드를 만든다.
 */
public final class EmailVerificationEvents {

    public static final String EVENT_TYPE = "EMAIL_VERIFICATION_REQUESTED";
    public static final String AGGREGATE_TYPE = "MEMBER";

    private EmailVerificationEvents() {}

    /** @param requestIp 발송을 요청한 IP. 처리기가 발급 행에 기록해 IP별 발송 한도를 센다. */
    public record Payload(String requestIp) {

        // IP는 개인정보라서 로그에 남기지 않는다.
        @Override
        public String toString() {
            return "Payload[****]";
        }
    }
}
