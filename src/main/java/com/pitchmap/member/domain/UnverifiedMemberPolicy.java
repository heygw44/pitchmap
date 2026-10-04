package com.pitchmap.member.domain;

import java.time.Duration;
import java.time.Instant;

/** 이메일 인증을 마치지 않은 계정을 언제까지 두는지 정하는 규칙이다. */
public final class UnverifiedMemberPolicy {

    /** 가입한 뒤 이 기간이 지나도록 인증하지 않은 계정은 삭제한다. */
    public static final Duration UNVERIFIED_RETENTION = Duration.ofDays(7);

    private UnverifiedMemberPolicy() {}

    /**
     * 호출하면 삭제 기준 시각을 돌려준다. 이 시각보다 먼저 가입한 미인증 계정만 삭제 대상이다.
     * 정확히 이 시각에 가입한 계정은 아직 7일을 채우지 못한 것이므로 남긴다.
     */
    public static Instant deletionCutoff(Instant now) {
        return now.minus(UNVERIFIED_RETENTION);
    }
}
