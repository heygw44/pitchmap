package com.pitchmap.trust.domain;

public interface MemberReportRepository {

    MemberReport saveAndFlush(MemberReport report);
}
