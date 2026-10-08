package com.pitchmap.trust.infra;

/**
 * 회원 한 명의 동행 기록을 세어 온 값이다. receivedReviews와 rejoinWanted는 숨기지 않았고 블라인드 공개가 풀린 후기만 센다.
 * recentEarlyLeaves는 기준 시각 이후의 임박 탈퇴 횟수다.
 */
public record TrustRecordRow(
        long completedCompanions, long receivedReviews, long rejoinWanted, long recentEarlyLeaves) {}
