package com.pitchmap.trust.application;

import com.pitchmap.trust.domain.CompanionReviewTag;
import java.util.List;

/** 동행 후기를 쓰려는 요청. tags와 comment는 없을 수 있고, 공백뿐인 comment는 서비스가 없는 것으로 저장한다. */
public record CompanionReviewWriteCommand(
        long revieweeId, boolean rejoinWanted, List<CompanionReviewTag> tags, String comment) {}
