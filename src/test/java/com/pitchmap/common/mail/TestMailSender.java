package com.pitchmap.common.mail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 통합 테스트가 실제 메일 서버 대신 쓰는 발송기. 보낸 메일을 메모리에 모으고, 지정한 횟수만큼 발송을 실패시킬 수 있다.
 *
 * <p>아웃박스 발행기와 테스트 스레드가 동시에 접근하므로 이 클래스는 스레드 안전해야 한다.
 * 통합 테스트는 Spring 컨텍스트를 공유하므로, 각 테스트는 시작할 때 {@link #reset()}을 호출해 이전 테스트가 남긴 메일을 지워야 한다.
 */
public class TestMailSender implements MailSender {

    public static final String FAILURE_MESSAGE = "simulated mail delivery failure";

    private final List<MailMessage> sent = new CopyOnWriteArrayList<>();
    private final AtomicInteger remainingFailures = new AtomicInteger();

    @Override
    public void send(MailMessage message) {
        if (shouldFail()) {
            throw new MailDeliveryException(FAILURE_MESSAGE);
        }
        sent.add(message);
    }

    /** 성공한 발송만 보낸 순서대로 돌려준다. 실패시킨 발송은 포함하지 않는다. */
    public List<MailMessage> sent() {
        return List.copyOf(sent);
    }

    /** 호출하면 앞으로 {@code count}번의 {@link #send} 호출이 {@link MailDeliveryException}을 던진다. */
    public void failNextSends(int count) {
        remainingFailures.set(count);
    }

    public void reset() {
        sent.clear();
        remainingFailures.set(0);
    }

    private boolean shouldFail() {
        return remainingFailures.getAndUpdate(remaining -> remaining > 0 ? remaining - 1 : 0) > 0;
    }
}
