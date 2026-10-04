package com.pitchmap.member.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 이메일 인증 코드 6자리. 원값은 메모리와 메일 본문에만 있어야 하므로 {@link #toString()}은 값을 숨긴다.
 * DB에는 {@link #hash(long, String)}의 결과만 저장한다.
 */
public record VerificationCode(String value) {

    public static final int LENGTH = 6;

    private static final int BOUND = 1_000_000;
    private static final String HASH_ALGORITHM = "SHA-256";

    public VerificationCode {
        if (!isWellFormed(value)) {
            throw new IllegalArgumentException("인증 코드는 숫자 " + LENGTH + "자리여야 합니다.");
        }
    }

    public static VerificationCode generate(SecureRandom random) {
        String digits = Integer.toString(random.nextInt(BOUND));
        return new VerificationCode("0".repeat(LENGTH - digits.length()) + digits);
    }

    /** 호출하면 코드가 정확히 숫자 6자리인지 알려 준다. 유니코드 숫자(예: 아라비아 숫자)는 받지 않는다. */
    public static boolean isWellFormed(String code) {
        if (code == null || code.length() != LENGTH) {
            return false;
        }
        return code.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    /**
     * 호출하면 {@code "{memberId}:{code}"}의 SHA-256을 소문자 16진수 64자로 돌려준다.
     * 코드가 6자리 숫자라서 경우의 수가 100만 개뿐이므로, 해시만 보고 코드를 되돌릴 수 있다.
     * 그래서 회원 ID를 함께 해시해 다른 회원의 해시와 섞어 쓰는 사전 계산을 막고, 5번 시도 제한으로 추측을 막는다.
     */
    public static String hash(long memberId, String code) {
        MessageDigest digest = newDigest();
        byte[] hashed = digest.digest((memberId + ":" + code).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashed);
    }

    /** 호출하면 입력한 코드의 해시와 저장된 해시를 같은 시간에 비교한다. 다른 위치에서 틀려도 걸리는 시간이 같아야 한다. */
    public static boolean matches(long memberId, String code, String storedHash) {
        if (code == null || storedHash == null) {
            return false;
        }
        byte[] expected = storedHash.getBytes(StandardCharsets.UTF_8);
        byte[] actual = hash(memberId, code).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM + " 알고리즘을 쓸 수 없습니다.", e);
        }
    }

    @Override
    public String toString() {
        return "VerificationCode[value=****]";
    }
}
