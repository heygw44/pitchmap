package com.pitchmap.common.testsupport;

import java.util.concurrent.atomic.AtomicLong;

/**
 * UNIQUE 컬럼의 기본값을 만드는 JVM 전역 순번. 난수가 아니라 순번이라 실패를 그대로 재현할 수 있다.
 *
 * <p>테스트 데이터 빌더는 생성 시점이 아니라 {@code build()} 시점에 값을 꺼내야 한다.
 * 그래야 빌더 하나로 서로 다른 행을 여러 개 만들 수 있다.
 */
public final class TestSequence {

    private static final AtomicLong COUNTER = new AtomicLong();

    private TestSequence() {}

    public static long next() {
        return COUNTER.incrementAndGet();
    }

    public static String email() {
        return "user-" + next() + "@example.com";
    }

    public static String nickname() {
        return "nick-" + next();
    }

    public static String unique(String prefix) {
        return prefix + "-" + next();
    }
}
