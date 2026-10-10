package com.pitchmap.program.application;

import java.util.List;

/** 신청자 목록의 한 페이지다. 전체 개수는 세지 않고, 다음 페이지가 있는지만 hasNext로 알려 준다. */
public record ProgramApplicantPage(List<ProgramApplicantItem> content, int page, int size, boolean hasNext) {}
