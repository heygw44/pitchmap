package com.pitchmap.notification.application;

import com.pitchmap.common.mail.MailDeliveryException;
import com.pitchmap.common.mail.MailSender;
import com.pitchmap.notification.infra.NotificationMailRow;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 저장된 알림 가운데 메일로 보낼 것을 골라 보내고, 보낸 알림에 보낸 시각을 기록한다.
 *
 * <p>이 클래스에는 {@code @Transactional}을 걸지 않는다. 메일 서버 응답을 기다리는 동안 DB 커넥션을 잡지 않으려고, 대상 조회와
 * 보낸 시각 기록은 {@link NotificationMailStore}의 짧은 트랜잭션으로 따로 하고 메일은 그 밖에서 보낸다.
 *
 * <p>보낼 대상은 아직 메일을 보내지 않았고, 받는 회원이 그 종류의 이메일을 받기로 했고(설정이 없으면 받는다), 이메일 인증을 마친
 * 회원이다. 설정은 보내는 시점에 읽으므로, 재시도 사이에 회원이 이메일을 끄면 남은 메일은 보내지 않는다.
 *
 * <p>한 통을 보내지 못해도 나머지는 계속 보낸다. 하나라도 실패했으면 마지막에 예외를 던져 발행기가 이벤트를 다시 처리하게 한다.
 * 다시 처리할 때는 보낸 시각이 기록된 알림을 건너뛰므로 보내지 못한 알림에만 메일이 나간다. 메일을 보낸 직후부터 시각을 기록하기 전에
 * 서버가 멈추면 같은 메일이 한 번 더 나갈 수 있다. 전달은 적어도 한 번이다.
 */
@Slf4j
@Component
class NotificationMailer {

    private final NotificationMailStore store;
    private final MailSender mailSender;
    private final String baseUrl;

    NotificationMailer(
            NotificationMailStore store, MailSender mailSender, @Value("${pitchmap.web.base-url}") String baseUrl) {
        this.store = store;
        this.mailSender = mailSender;
        this.baseUrl = baseUrl;
    }

    /**
     * 호출하면 dedupKeys에 해당하는 알림 가운데 메일을 보낼 것을 모두 보낸다.
     *
     * @throws MailDeliveryException 한 통이라도 보내거나 기록하지 못했을 때. 메시지에는 건수와 알림 ID만 담는다.
     */
    void sendPending(Collection<String> dedupKeys) {
        List<NotificationMailRow> targets = store.findTargets(dedupKeys);
        List<Long> failedIds = new ArrayList<>();
        for (NotificationMailRow target : targets) {
            if (!sendOne(target)) {
                failedIds.add(target.notificationId());
            }
        }
        if (!failedIds.isEmpty()) {
            throw new MailDeliveryException("알림 메일 %d건 중 %d건을 보내지 못했습니다. notificationIds=%s"
                    .formatted(targets.size(), failedIds.size(), failedIds));
        }
    }

    // 발송기 구현마다 던지는 예외가 다르고, 한 통의 실패가 나머지 발송을 막으면 안 된다. 그래서 여기서만 RuntimeException을 통째로 잡는다.
    // 예외 메시지에는 받는 사람의 주소가 들어 있을 수 있으므로 로그에는 알림 ID만 남긴다.
    private boolean sendOne(NotificationMailRow target) {
        try {
            mailSender.send(NotificationMailFactory.create(target, baseUrl));
            store.markSent(target.notificationId());
            return true;
        } catch (RuntimeException e) {
            log.warn("notification mail failed notificationId={}", target.notificationId());
            return false;
        }
    }
}
