package com.pitchmap.common.architecture.fixture.compliant.member.application;

import com.pitchmap.common.architecture.fixture.compliant.common.support.Slug;
import com.pitchmap.common.architecture.fixture.compliant.member.domain.Member;
import org.springframework.transaction.annotation.Transactional;

public class MemberLookup {

    @Transactional
    public Member find(String value) {
        return new Member(new Slug(value));
    }
}
