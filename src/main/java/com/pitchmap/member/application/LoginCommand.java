package com.pitchmap.member.application;

public record LoginCommand(String email, String password, String ip) {

    // 레코드 기본 toString은 모든 구성요소를 찍는다. 그래서 이메일·비밀번호가 로그에 새지 않도록 우리가 재정의했다.
    @Override
    public String toString() {
        return "LoginCommand[ip=" + ip + "]";
    }
}
