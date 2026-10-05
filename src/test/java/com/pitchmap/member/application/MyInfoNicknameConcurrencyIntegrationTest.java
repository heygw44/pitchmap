package com.pitchmap.member.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.common.web.PatchField;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.domain.MemberException;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class MyInfoNicknameConcurrencyIntegrationTest {

    private static final int THREADS = 10;

    @Autowired
    MyInfoService myInfoService;

    @Autowired
    MemberJpaRepository memberJpaRepository;

    @RepeatedTest(5)
    @DisplayName("여러 회원이 같은 새 닉네임으로 동시에 바꾸면 1명만 성공하고 나머지는 MEMBER_NICKNAME_DUPLICATED")
    void onlyOneMemberGetsSameNewNickname() throws Exception {
        // given
        String nickname = TestSequence.nickname();
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            memberIds.add(memberJpaRepository.saveAndFlush(aMember().build()).getId());
        }

        // when
        List<Throwable> failures = changeNicknameConcurrently(memberIds, nickname);

        // then
        assertThat(failures)
                .hasSize(THREADS - 1)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                MemberException.class,
                                e -> assertThat(e.getErrorCode())
                                        .isEqualTo(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED)));
        List<Member> owners = memberJpaRepository.findAllById(memberIds).stream()
                .filter(member -> member.getNickname().equals(nickname))
                .toList();
        assertThat(owners).hasSize(1);
    }

    private List<Throwable> changeNicknameConcurrently(List<Long> memberIds, String nickname) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        MyInfoUpdateCommand command =
                new MyInfoUpdateCommand(PatchField.of(nickname), PatchField.absent(), PatchField.absent());
        try {
            List<Future<MyInfo>> futures = new ArrayList<>();
            for (long memberId : memberIds) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return myInfoService.update(memberId, command);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return collectFailures(futures);
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private List<Throwable> collectFailures(List<Future<MyInfo>> futures) throws InterruptedException {
        List<Throwable> failures = new ArrayList<>();
        for (Future<MyInfo> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            } catch (TimeoutException e) {
                failures.add(e);
            }
        }
        return failures;
    }
}
