package com.pitchmap.trust.infra;

/** 태그 하나가 공개된 후기에 나온 횟수다. tag는 태그 이름 문자열이다. */
public record TagCountRow(String tag, long count) {}
