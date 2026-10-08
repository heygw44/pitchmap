package com.pitchmap.basecamp.application;

/**
 * 베이스캠프를 고치려는 요청 내용이다. 값이 null인 필드는 현재 값을 그대로 둔다.
 * 합류 조건은 보내면 조건 전체를 바꾸며, 그 안의 null 값은 해당 조건을 걸지 않는다는 뜻이다.
 */
public record BasecampReviseCommand(
        String title, String description, BasecampOpenCommand.JoinConditionCommand joinCondition, Integer capacity) {}
