package com.pitchmap.member.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Email {

    private static final char AT = '@';
    private static final char DOT = '.';
    private static final char IP_LITERAL_START = '[';

    /**
     * 요청 검증용 정규식이다. 도메인에 점이 있고 대괄호가 없어야 통과한다.
     * 점이 없는 도메인(localhost)과 IP 주소 도메인({@code a@[127.0.0.1]})은 인증 메일을 받을 수 없고,
     * 일회용 도메인 검사도 비껴가므로 가입을 받지 않는다.
     */
    public static final String DOMAIN_PATTERN = ".+@[^@\\[\\]]*\\.[^@\\[\\]]*";

    private final String value;

    private Email(String value) {
        this.value = value;
    }

    public static Email of(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("이메일이 null입니다.");
        }
        int at = raw.lastIndexOf(AT);
        if (at <= 0 || at == raw.length() - 1 || !hasMailableDomain(raw.substring(at + 1))) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
        }
        return new Email(raw.toLowerCase(Locale.ROOT));
    }

    private static boolean hasMailableDomain(String domain) {
        return domain.indexOf(DOT) >= 0 && domain.indexOf(IP_LITERAL_START) < 0;
    }

    public String value() {
        return value;
    }

    public String domain() {
        return value.substring(value.lastIndexOf(AT) + 1);
    }

    /** 호출하면 도메인과 그 상위 도메인 중 라벨이 2개 이상인 것만 돌려준다. 최상위 라벨 단독(com)은 넣지 않는다. */
    public List<String> domainCandidates() {
        List<String> candidates = new ArrayList<>();
        String current = domain();
        while (current.indexOf(DOT) >= 0) {
            candidates.add(current);
            current = current.substring(current.indexOf(DOT) + 1);
        }
        return candidates;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Email other && value.equals(other.value));
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "Email[***]";
    }
}
