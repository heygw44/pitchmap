package com.pitchmap.member.application;

/**
 * 새로 발급한 인증 코드와 받을 이메일. 코드 원값을 담고 있어서, 호출한 쪽은 메일 본문을 만든 뒤 버려야 한다.
 * 이메일과 코드는 로그에 남기지 않는다.
 */
public record IssuedVerification(String email, String code) {

    @Override
    public String toString() {
        return "IssuedVerification[****]";
    }
}
