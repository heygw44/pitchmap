package com.pitchmap.basecamp.domain;

/** 베이스캠프를 연 뒤에도 고칠 수 있는 내용이다. 출발일과 장소는 바꿀 수 없어서 담지 않는다. */
public record BasecampRevision(String title, String description, Capacity capacity, JoinCondition joinCondition) {}
