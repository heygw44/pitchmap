package com.pitchmap.common.architecture.fixture.compliant.trust.application;

import com.pitchmap.common.architecture.fixture.compliant.member.application.MemberLookup;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class TrustScoreService {

    public TrustScoreService(MemberLookup lookup) {}
}
