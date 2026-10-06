package com.pitchmap.member.domain;

public final class Password {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 64;

    private static final char PRINTABLE_ASCII_START = 0x21;
    private static final char PRINTABLE_ASCII_END = 0x7E;

    private final String value;

    private Password(String value) {
        this.value = value;
    }

    public static Password of(String raw) {
        if (raw == null || raw.length() < MIN_LENGTH || raw.length() > MAX_LENGTH) {
            throw policyViolation();
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < PRINTABLE_ASCII_START || c > PRINTABLE_ASCII_END) {
                throw policyViolation();
            }
            if (isAsciiLetter(c)) {
                hasLetter = true;
            } else if (c >= '0' && c <= '9') {
                hasDigit = true;
            } else {
                hasSpecial = true;
            }
        }
        if (!(hasLetter && hasDigit && hasSpecial)) {
            throw policyViolation();
        }
        return new Password(raw);
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static MemberException policyViolation() {
        return new MemberException(MemberErrorCode.MEMBER_PASSWORD_POLICY);
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return "Password[***]";
    }
}
