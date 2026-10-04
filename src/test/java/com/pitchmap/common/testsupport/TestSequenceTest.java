package com.pitchmap.common.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestSequenceTest {

    @Test
    @DisplayName("한 스레드에서는 값이 계속 커진다")
    void valuesStrictlyIncreaseWithinOneThread() {
        // given
        long first = TestSequence.next();

        // when
        long second = TestSequence.next();
        long third = TestSequence.next();

        // then
        assertThat(first).isLessThan(second);
        assertThat(second).isLessThan(third);
    }

    @Test
    @DisplayName("여러 스레드가 동시에 불러도 값이 겹치지 않는다")
    void concurrentCallsNeverRepeat() throws Exception {
        // given
        int threads = 8;
        int callsPerThread = 500;
        Set<Long> seen = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        // when
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> collect(seen, start, callsPerThread)));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        // then
        assertThat(seen).hasSize(threads * callsPerThread);
    }

    @Test
    @DisplayName("이메일과 닉네임은 호출마다 달라지고 회원 컬럼 길이(254/20)에 들어간다")
    void emailAndNicknameAreUniqueAndFitColumns() {
        // given
        int count = 1000;
        Set<String> emails = ConcurrentHashMap.newKeySet();
        Set<String> nicknames = ConcurrentHashMap.newKeySet();

        // when
        for (int i = 0; i < count; i++) {
            emails.add(TestSequence.email());
            nicknames.add(TestSequence.nickname());
        }

        // then
        assertThat(emails)
                .hasSize(count)
                .allSatisfy(email -> assertThat(email.length()).isLessThanOrEqualTo(254));
        assertThat(nicknames)
                .hasSize(count)
                .allSatisfy(nick -> assertThat(nick.length()).isLessThanOrEqualTo(20));
    }

    private static void collect(Set<Long> seen, CountDownLatch start, int calls) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        for (int i = 0; i < calls; i++) {
            seen.add(TestSequence.next());
        }
    }
}
