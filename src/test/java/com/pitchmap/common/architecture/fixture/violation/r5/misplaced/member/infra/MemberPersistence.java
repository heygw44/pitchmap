package com.pitchmap.common.architecture.fixture.violation.r5.misplaced.member.infra;

import org.springframework.transaction.annotation.Transactional;

public class MemberPersistence {

    @Transactional
    public void save() {}
}
