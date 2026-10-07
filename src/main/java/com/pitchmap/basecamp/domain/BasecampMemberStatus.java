package com.pitchmap.basecamp.domain;

/** 멤버의 활동 상태다. 탈퇴하거나 강퇴된 멤버의 행은 지우지 않고 남긴다. */
public enum BasecampMemberStatus {
    ACTIVE,
    LEFT,
    KICKED
}
