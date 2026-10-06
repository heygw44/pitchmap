package com.pitchmap.member.application;

/**
 * 새로 발급한 재설정 토큰과 받을 이메일. 토큰 원값을 담고 있어서, 호출한 쪽은 메일 본문을 만든 뒤 버려야 한다.
 * 이메일과 토큰은 로그에 남기지 않는다.
 */
public record IssuedPasswordReset(String email, String token) {

    @Override
    public String toString() {
        return "IssuedPasswordReset[****]";
    }
}
