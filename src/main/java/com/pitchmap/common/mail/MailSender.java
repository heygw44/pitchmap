package com.pitchmap.common.mail;

/**
 * 메일을 보내는 포트. 모듈은 SMTP 같은 전송 방식을 모르고 이 인터페이스만 쓴다.
 * 테스트는 이 인터페이스를 구현한 대체물로 실제 메일 서버 없이 보낸 메일을 확인한다.
 */
public interface MailSender {

    /**
     * 메일을 한 통 보낸다. 서버가 메일을 받아들일 때까지 기다리고, 돌아오면 전송이 끝난 것이다.
     *
     * @throws MailDeliveryException 보내지 못했을 때. 호출하는 쪽은 이 예외로 재시도 여부를 정한다.
     */
    void send(MailMessage message);
}
