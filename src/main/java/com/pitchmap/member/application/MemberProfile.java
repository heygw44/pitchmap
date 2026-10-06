package com.pitchmap.member.application;

import com.pitchmap.member.domain.Member;

/**
 * 다른 회원에게 보여 줄 수 있는 회원 정보다. 이메일처럼 공개하면 안 되는 값은 담지 않는다.
 * 자기 신고 값은 다른 모듈도 받아 쓰므로 enum 대신 이름 문자열로 담고, 회원이 밝히지 않았으면 null이다.
 */
public record MemberProfile(long memberId, String nickname, String selfAgeGroup, String selfGender) {

    static MemberProfile from(Member member) {
        return new MemberProfile(
                member.getId(),
                member.getNickname(),
                nameOrNull(member.getSelfAgeGroup()),
                nameOrNull(member.getSelfGender()));
    }

    private static String nameOrNull(Enum<?> value) {
        if (value == null) {
            return null;
        }
        return value.name();
    }
}
