package com.pitchmap.member.domain;

import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import java.time.Instant;

public class MemberBuilder {

    private static final String DEFAULT_PASSWORD_HASH = "$2a$10$" + "a".repeat(53);

    private String email;
    private String passwordHash = DEFAULT_PASSWORD_HASH;
    private String nickname;
    private Instant now = MutableClock.DEFAULT_INSTANT;

    public static MemberBuilder aMember() {
        return new MemberBuilder();
    }

    public MemberBuilder email(String email) {
        this.email = email;
        return this;
    }

    public MemberBuilder passwordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        return this;
    }

    public MemberBuilder nickname(String nickname) {
        this.nickname = nickname;
        return this;
    }

    public MemberBuilder now(Instant now) {
        this.now = now;
        return this;
    }

    public Member build() {
        String resolvedEmail = email != null ? email : TestSequence.email();
        String resolvedNickname = nickname != null ? nickname : TestSequence.nickname();
        return Member.register(Email.of(resolvedEmail), passwordHash, resolvedNickname, now);
    }
}
