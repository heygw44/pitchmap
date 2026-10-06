package com.pitchmap.member.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** 비밀번호 재설정 링크에 싣는 토큰 원값. 원값은 메일로만 보내고, DB에는 {@link #hash(String)}의 결과만 저장한다. */
public final class ResetToken {

    // 32바이트를 패딩 없이 base64url로 인코딩하면 43자다.
    private static final Pattern WELL_FORMED = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final String value;

    private ResetToken(String value) {
        this.value = value;
    }

    public static ResetToken generate(SecureRandom random) {
        byte[] bytes = new byte[PasswordResetPolicy.TOKEN_BYTES];
        random.nextBytes(bytes);
        return new ResetToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /** 호출하면 원값의 SHA-256을 소문자 16진수 64자로 돌려준다. 원값이 이미 무작위 256비트라서 솔트 없이 해시해도 된다. */
    public static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);
        }
    }

    /** 호출하면 {@link #generate(SecureRandom)}가 만드는 형식인지 알려 준다. null이면 false다. */
    public static boolean isWellFormed(String raw) {
        return raw != null && WELL_FORMED.matcher(raw).matches();
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return "ResetToken[****]";
    }
}
