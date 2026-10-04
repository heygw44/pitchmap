package com.pitchmap.member.application;

public record SignupCommand(String email, String password, String nickname, String requestIp) {

    @Override
    public String toString() {
        return "SignupCommand[nickname=" + nickname + "]";
    }
}
