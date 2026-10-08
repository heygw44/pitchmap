package com.pitchmap.basecamp.application;

/** 상태를 바꾸거나 내용을 고친 뒤 베이스캠프의 ID와 상태 이름이다. */
public record BasecampStatusResult(long basecampId, String status) {}
