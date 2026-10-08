package com.pitchmap.member.domain;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MemberSuspendTemporarilyTest {

    private static final Instant NOW = Instant.parse("2026-10-05T03:00:00Z");
    private static final Instant UNTIL = NOW.plus(Duration.ofHours(72));

    @Test
    @DisplayName("[SN-05] 정지 중이 아닌 회원은 SUSPENDED가 되고 종료 시각과 수정 시각이 바뀐다")
    void suspendsActiveMember() {
        Member member = memberWithStatus(MemberStatus.ACTIVE, null);

        member.suspendTemporarily(UNTIL, NOW);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.getSuspendedUntil()).isEqualTo(UNTIL);
        assertThat(member.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[SN-05] 미인증 회원도 SUSPENDED가 된다")
    void suspendsUnverifiedMember() {
        Member member = memberWithStatus(MemberStatus.UNVERIFIED, null);

        member.suspendTemporarily(UNTIL, NOW);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
    }

    @Test
    @DisplayName("[SN-05] 이미 더 일찍 끝나는 정지 중이면 종료 시각을 늦춘다")
    void extendsEarlierSuspension() {
        Member member = memberWithStatus(MemberStatus.SUSPENDED, NOW.plus(Duration.ofHours(10)));

        member.suspendTemporarily(UNTIL, NOW);

        assertThat(member.getSuspendedUntil()).isEqualTo(UNTIL);
    }

    @Test
    @DisplayName("[SN-05] 이미 더 늦게 끝나는 정지 중이면 종료 시각을 줄이지 않는다")
    void keepsLaterSuspension() {
        Instant later = NOW.plus(Duration.ofDays(30));
        Member member = memberWithStatus(MemberStatus.SUSPENDED, later);
        Instant before = member.getUpdatedAt();

        member.suspendTemporarily(UNTIL, NOW);

        assertThat(member.getSuspendedUntil()).isEqualTo(later);
        assertThat(member.getUpdatedAt()).isEqualTo(before);
    }

    @Test
    @DisplayName("[SN-05] 종료 시각이 없는 영구 정지는 그대로 둔다")
    void keepsPermanentSuspension() {
        Member member = memberWithStatus(MemberStatus.SUSPENDED, null);

        member.suspendTemporarily(UNTIL, NOW);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(member.getSuspendedUntil()).isNull();
    }

    @Test
    @DisplayName("[SN-05] 탈퇴한 회원은 그대로 둔다")
    void keepsWithdrawnMember() {
        Member member = memberWithStatus(MemberStatus.WITHDRAWN, null);

        member.suspendTemporarily(UNTIL, NOW);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getSuspendedUntil()).isNull();
    }

    @Test
    @DisplayName("종료 시각이나 수정 시각이 null이면 거부한다")
    void rejectsNull() {
        Member member = memberWithStatus(MemberStatus.ACTIVE, null);

        assertThatThrownBy(() -> member.suspendTemporarily(null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> member.suspendTemporarily(UNTIL, null)).isInstanceOf(IllegalArgumentException.class);
    }

    // 상태를 바꾸는 공개 메서드가 아직 없어서, 테스트가 필요한 상태를 필드에 직접 넣는다.
    private static Member memberWithStatus(MemberStatus status, Instant suspendedUntil) {
        Member member = aMember().build();
        ReflectionTestUtils.setField(member, "status", status);
        ReflectionTestUtils.setField(member, "suspendedUntil", suspendedUntil);
        return member;
    }
}
