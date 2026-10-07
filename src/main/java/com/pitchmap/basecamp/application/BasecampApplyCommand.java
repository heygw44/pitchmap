package com.pitchmap.basecamp.application;

/** 합류 신청 요청이다. message는 없을 수 있다. */
public record BasecampApplyCommand(long basecampId, long memberId, String message) {}
