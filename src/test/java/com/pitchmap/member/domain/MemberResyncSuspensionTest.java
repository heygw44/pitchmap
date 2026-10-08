package com.pitchmap.member.domain;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MemberResyncSuspensionTest {

    private static final Instant NOW = Instant.parse("2026-10-05T03:00:00Z");
    private static final Instant VERIFIED_AT = NOW.minus(Duration.ofDays(30));

    @Test
    @DisplayName("[SN-05][SN-15] 영구 정지가 남았으면 종료 시각 없이 SUSPENDED로 둔다")
    void permanentRemainsSuspendedWithoutEnd() {
        Member member = memberWith(MemberStatus.SUSPENDED, NOW.plus(Duration.ofDays(3)), VERIFIED_AT);

        member.resyncSuspension(true, NOW.plus(Duration.ofDays(1)), NOW);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.getSuspendedUntil()).isNull();
        assertThat(member.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[SN-05][SN-15] 남은 정지의 종료 시각이 지금보다 늦으면 그 시각까지 정지하고, 기존 종료 시각보다 짧아질 수 있다")
    void suspendsUntilLatestRemainingEnd() {
        Instant remainingEnd = NOW.plus(Duration.ofHours(10));
        Member member = memberWith(MemberStatus.SUSPENDED, NOW.plus(Duration.ofDays(3)), VERIFIED_AT);

        member.resyncSuspension(false, remainingEnd, NOW);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.getSuspendedUntil()).isEqualTo(remainingEnd);
    }

    @Test
    @DisplayName("[SN-05][SN-15] 남은 정지가 없으면 정지를 풀고, 이메일 인증을 마친 회원은 ACTIVE로 돌아간다")
    void releasesVerifiedMemberToActive() {
        Member member = memberWith(MemberStatus.SUSPENDED, NOW.plus(Duration.ofDays(3)), VERIFIED_AT);

        member.resyncSuspension(false, null, NOW);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getSuspendedUntil()).isNull();
        assertThat(member.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[SN-05][SN-15] 이메일 인증을 마치지 못한 회원은 정지가 풀리면 UNVERIFIED로 돌아간다")
    void releasesUnverifiedMemberToUnverified() {
        Member member = memberWith(MemberStatus.SUSPENDED, null, null);

        member.resyncSuspension(false, null, NOW);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.UNVERIFIED);
        assertThat(member.getSuspendedUntil()).isNull();
    }

    @Test
    @DisplayName("[SN-15] 남은 정지의 종료 시각이 지금과 같으면 이미 끝난 정지로 보고 풀어 준다")
    void remainingEndEqualToNowIsReleased() {
        Member member = memberWith(MemberStatus.SUSPENDED, NOW.plus(Duration.ofDays(1)), VERIFIED_AT);

        member.resyncSuspension(false, NOW, NOW);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getSuspendedUntil()).isNull();
    }

    @Test
    @DisplayName("[SN-15] 정지 중이 아닌 회원은 남은 정지가 없으면 그대로 두고, 탈퇴한 회원은 어떤 경우에도 바꾸지 않는다")
    void keepsNonSuspendedAndWithdrawnMembers() {
        Member active = memberWith(MemberStatus.ACTIVE, null, VERIFIED_AT);
        Member withdrawn = memberWith(MemberStatus.WITHDRAWN, null, VERIFIED_AT);

        active.resyncSuspension(false, null, NOW);
        withdrawn.resyncSuspension(true, NOW.plus(Duration.ofDays(1)), NOW);

        assertThat(active.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(withdrawn.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(withdrawn.getSuspendedUntil()).isNull();
    }

    @Test
    @DisplayName("현재 시각이 null이면 거부한다")
    void rejectsNullNow() {
        Member member = memberWith(MemberStatus.SUSPENDED, null, VERIFIED_AT);

        assertThatThrownBy(() -> member.resyncSuspension(false, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 상태를 바꾸는 공개 메서드가 아직 없어서, 테스트가 필요한 상태를 필드에 직접 넣는다.
    private static Member memberWith(MemberStatus status, Instant suspendedUntil, Instant emailVerifiedAt) {
        Member member = aMember().build();
        ReflectionTestUtils.setField(member, "status", status);
        ReflectionTestUtils.setField(member, "suspendedUntil", suspendedUntil);
        ReflectionTestUtils.setField(member, "emailVerifiedAt", emailVerifiedAt);
        return member;
    }
}
