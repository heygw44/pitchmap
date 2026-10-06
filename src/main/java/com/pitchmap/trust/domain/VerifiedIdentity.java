package com.pitchmap.trust.domain;

/**
 * 제공자가 확인해 돌려준 신원이다.
 *
 * @param ci 본인확인기관의 고유 식별값 원값. 저장하지 않고 해시로 바꾼 뒤 버린다.
 * @param birthYear 출생연도
 * @param gender 성별
 */
public record VerifiedIdentity(String ci, int birthYear, Gender gender) {

    /** CI 원값과 출생연도가 로그에 남지 않게 가린다. */
    @Override
    public String toString() {
        return "VerifiedIdentity[ci=****, birthYear=****, gender=" + gender + "]";
    }
}
