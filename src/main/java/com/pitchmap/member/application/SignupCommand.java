package com.pitchmap.member.application;

public record SignupCommand(String email, String password, String nickname) {

    @Override
    public String toString() {
        return "SignupCommand[nickname=" + nickname + "]";
    }
}
