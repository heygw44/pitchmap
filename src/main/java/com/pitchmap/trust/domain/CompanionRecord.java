package com.pitchmap.trust.domain;

/**
 * 회원의 동행 기록이다. 신뢰 단계 2 조건을 판정하는 데 쓴다.
 *
 * @param completedCompanions 완료한 동행 횟수
 * @param receivedReviews 받은 동행 후기 수
 * @param rejoinWanted 받은 후기 중 "다시 동행하고 싶다"고 답한 수
 * @param recentSanction 최근 180일 안에 확정된 제재가 있으면 true
 */
public record CompanionRecord(int completedCompanions, int receivedReviews, int rejoinWanted, boolean recentSanction) {

    /** 단계 2가 되려면 완료한 동행이 이 횟수 이상이어야 한다. */
    public static final int REQUIRED_COMPLETED_COMPANIONS = 3;

    /** 단계 2가 되려면 받은 후기 중 "다시 동행" 비율이 이 퍼센트 이상이어야 한다. */
    public static final int REQUIRED_REJOIN_PERCENT = 80;

    /** 동행·후기·제재 기록이 하나도 없는 상태다. */
    public static final CompanionRecord NONE = new CompanionRecord(0, 0, 0, false);

    /** "다시 동행" 비율을 정수 %로 버림해서 돌려준다. 받은 후기가 없으면 비율을 정할 수 없으므로 null이다. */
    public Integer rejoinRate() {
        if (receivedReviews == 0) {
            return null;
        }
        return (int) ((long) rejoinWanted * 100 / receivedReviews);
    }

    /**
     * 단계 2 조건을 모두 채웠으면 true다. 버림한 비율이 아니라 개수로 비교하므로, 79.5%가 80%로 보이면서
     * 미충족인 경우가 없다. 받은 후기가 없으면 미충족이다.
     */
    public boolean meetsTrustedCondition() {
        return completedCompanions >= REQUIRED_COMPLETED_COMPANIONS
                && receivedReviews > 0
                && (long) rejoinWanted * 100 >= (long) REQUIRED_REJOIN_PERCENT * receivedReviews
                && !recentSanction;
    }
}
