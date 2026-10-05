package com.pitchmap.member.application;

import com.pitchmap.member.domain.Member;

/**
 * 회원 본인에게 보여 줄 내 정보다. 다른 모듈도 받아 쓰므로 상태·권한·자기 신고 값은 enum 대신 이름 문자열로 담는다.
 * 자기 신고 값은 회원이 밝히지 않았으면 null이다.
 */
public record MyInfo(
        long memberId,
        String email,
        String nickname,
        String status,
        String role,
        String selfAgeGroup,
        String selfGender) {

    static MyInfo from(Member member) {
        return new MyInfo(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getStatus().name(),
                member.getRole().name(),
                nameOrNull(member.getSelfAgeGroup()),
                nameOrNull(member.getSelfGender()));
    }

    private static String nameOrNull(Enum<?> value) {
        if (value == null) {
            return null;
        }
        return value.name();
    }

    // 레코드 기본 toString은 모든 구성요소를 찍는다. 그래서 이메일이 로그에 새지 않도록 이메일을 빼고 재정의했다.
    @Override
    public String toString() {
        return "MyInfo[memberId=%d, nickname=%s, status=%s, role=%s, selfAgeGroup=%s, selfGender=%s]"
                .formatted(memberId, nickname, status, role, selfAgeGroup, selfGender);
    }
}
