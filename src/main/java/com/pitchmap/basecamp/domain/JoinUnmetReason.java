package com.pitchmap.basecamp.domain;

/**
 * 회원이 베이스캠프에 합류 신청을 할 수 없는 이유다. 선언 순서가 응답에 나가는 순서다.
 *
 * <ul>
 *   <li>TRUST_LEVEL: 신뢰 단계가 부족하다. 본인확인 전이거나 미성년인 회원은 단계 0이라 항상 여기에 걸린다.
 *   <li>AGE_GROUP: 본인확인한 연령대가 없거나 캠프 리더가 건 연령대 범위 밖이다.
 *   <li>GENDER: 본인확인한 성별이 없거나 동성만 받는 조건의 성별과 다르다.
 *   <li>DATE_CONFLICT: 같은 기간에 확정된 다른 베이스캠프의 멤버다.
 *   <li>ALREADY_JOINED: 이미 멤버이거나 대기 중인 신청이 있다.
 *   <li>REAPPLY_NOT_ALLOWED: 거절·탈퇴·강퇴된 적이 있어 다시 신청할 수 없다.
 * </ul>
 */
public enum JoinUnmetReason {
    TRUST_LEVEL,
    AGE_GROUP,
    GENDER,
    DATE_CONFLICT,
    ALREADY_JOINED,
    REAPPLY_NOT_ALLOWED
}
