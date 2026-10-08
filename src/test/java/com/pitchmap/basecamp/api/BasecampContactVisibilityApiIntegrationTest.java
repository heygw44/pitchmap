package com.pitchmap.basecamp.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class BasecampContactVisibilityApiIntegrationTest {

    private static final String CONTACT = "https://open.kakao.com/o/abc";
    // MutableClock 기본 시각(2026-10-05T03:00:00Z)을 UTC 컬럼 값으로 쓴 것이다. 시점별 시각은 이 값에서 옮긴다.
    private static final String COMPLETED_AT = "2026-10-05 03:00:00";

    private enum Relation {
        ANONYMOUS("비회원"),
        UNRELATED("관계 없는 회원"),
        PENDING_APPLICANT("대기 신청자"),
        REJECTED_APPLICANT("거절된 신청자"),
        LEFT_MEMBER("탈퇴한 멤버"),
        MEMBER("멤버"),
        LEADER("캠프 리더");

        private final String label;

        Relation(String label) {
            this.label = label;
        }

        boolean isActiveParticipant() {
            return this == MEMBER || this == LEADER;
        }
    }

    // 현재 시각을 완료 시각 기준으로 얼마나 옮길지는 completedOffset이다. 완료 상태가 아니면 null이다.
    private enum Timing {
        RECRUITING("모집 중", "RECRUITING", null, false),
        CLOSED("마감", "CLOSED", null, false),
        CONFIRMED("확정", "CONFIRMED", null, true),
        COMPLETED_NOW("완료 직후", "COMPLETED", Duration.ZERO, true),
        COMPLETED_7D("완료 후 정확히 7일", "COMPLETED", Duration.ofDays(7), true),
        COMPLETED_7D_1S("완료 후 7일 1초", "COMPLETED", Duration.ofDays(7).plusSeconds(1), false),
        CANCELED("취소", "CANCELED", null, false);

        private final String label;
        private final String status;
        private final Duration completedOffset;
        private final boolean contactOpen;

        Timing(String label, String status, Duration completedOffset, boolean contactOpen) {
            this.label = label;
            this.status = status;
            this.completedOffset = completedOffset;
            this.contactOpen = contactOpen;
        }
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    static Stream<Arguments> matrix() {
        return Stream.of(Relation.values())
                .flatMap(relation -> Stream.of(Timing.values()).map(timing -> {
                    boolean visible = relation.isActiveParticipant() && timing.contactOpen;
                    String name =
                            "%s / %s -> 연락 수단 %s".formatted(relation.label, timing.label, visible ? "공개" : "필드 없음");
                    return Arguments.argumentSet(name, relation, timing, visible);
                }));
    }

    @ParameterizedTest
    @MethodSource("matrix")
    @DisplayName("[F-14][BC-23][NFR-11] 연락 수단은 확정 또는 완료 후 7일 안의 활성 멤버·캠프 리더에게만 보이고, 나머지는 필드 자체가 없다")
    void contactInfoVisibility(Relation relation, Timing timing, boolean expectedVisible) {
        // given
        BasecampApiFixture fixture =
                new BasecampApiFixture(mvc, memberRepository, passwordEncoder, emailVerificationService, jdbc);
        Member leader = fixture.saveMember();
        Member viewer = relation == Relation.LEADER ? leader : fixture.saveMember();
        long spotId = fixture.insertSpot("개머리언덕", 37.25, 127.25);
        long id = fixture.insertBasecamp(leader, spotId, timing.status, LocalDate.of(2026, 10, 20));
        fixture.setContactInfo(id, CONTACT);
        if (timing.completedOffset != null) {
            jdbc.update("UPDATE basecamp SET completed_at = ? WHERE id = ?", COMPLETED_AT, id);
        }
        switch (relation) {
            case PENDING_APPLICANT -> fixture.insertApplicationRow(id, viewer, "PENDING");
            case REJECTED_APPLICANT -> fixture.insertApplicationRow(id, viewer, "REJECTED");
            case LEFT_MEMBER -> fixture.insertMemberRow(id, viewer, "MEMBER", "LEFT");
            case MEMBER -> fixture.insertMemberRow(id, viewer, "MEMBER", "ACTIVE");
            default -> {}
        }
        Cookie session = relation == Relation.ANONYMOUS ? null : fixture.login(viewer);
        if (timing.completedOffset != null) {
            clock.setInstant(MutableClock.DEFAULT_INSTANT.plus(timing.completedOffset));
        }

        // when
        var request = mvc.get().uri("/api/basecamps/" + id);
        MvcTestResult result = (session == null ? request : request.cookie(session)).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        if (expectedVisible) {
            assertThat(result).bodyJson().extractingPath("$.contactInfo").isEqualTo(CONTACT);
        } else {
            assertThat(result).bodyJson().doesNotHavePath("$.contactInfo");
        }
    }
}
