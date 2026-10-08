package com.pitchmap.trust.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.notification.application.OutboxPublisher;
import com.pitchmap.trust.domain.SanctionType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SanctionNotificationIntegrationTest {

    @Autowired
    private SanctionConfirmService sanctionConfirmService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long adminId;
    private long targetId;

    @BeforeEach
    void setUp() {
        CompanionReviewFixture fixture = new CompanionReviewFixture(jdbc, memberRepository);
        adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        targetId = fixture.saveVerifiedMember(TestSequence.nickname());
    }

    @Test
    @DisplayName("[F-20][SN-10] 경고를 확정하고 이벤트를 발행하면 제재받은 회원의 알림함에 경고 알림이 생긴다")
    void warningNotifiesSanctionedMember() {
        // when
        confirm(SanctionType.WARNING);
        outboxPublisher.publishPending();

        // then
        List<Map<String, Object>> notifications =
                jdbc.queryForList("SELECT member_id, type, title, body, link FROM notification");
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0))
                .containsEntry("member_id", targetId)
                .containsEntry("type", "SANCTION_CONFIRMED")
                .containsEntry("title", "이용 제재 안내")
                .containsEntry("body", "경고를 받았습니다.")
                .containsEntry("link", null);
    }

    @Test
    @DisplayName("[F-20][SN-10] 7일 정지를 확정하면 알림 본문에 한국 시간 종료 시각이 들어간다")
    void suspensionNotificationShowsEndInKoreanTime() {
        // given: 테스트 시계는 2026-10-05 12:00(한국 시간)이라 7일 뒤는 2026-10-12 12:00이다.
        confirm(SanctionType.WARNING);
        confirm(SanctionType.SUSPEND_7D);

        // when
        outboxPublisher.publishPending();

        // then
        List<String> bodies = jdbc.queryForList("SELECT body FROM notification ORDER BY id", String.class);
        assertThat(bodies).containsExactly("경고를 받았습니다.", "2026-10-12 12:00까지 이용이 정지됩니다.");
    }

    private void confirm(SanctionType type) {
        sanctionConfirmService.confirm(new SanctionConfirmCommand(targetId, null, type, "욕설", adminId));
    }
}
