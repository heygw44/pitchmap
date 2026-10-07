package com.pitchmap.trust.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.trust.domain.Gender;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class TmpDeadlockStressIntegrationTest {

    private static final int THREADS = 10;
    private static final int ROUNDS = 300;

    @Autowired
    private IdentityVerificationService service;

    @Autowired
    private IdentityVerificationApplier applier;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void stress() throws Exception {
        System.out.println("STRESS before(applier) " + run("a", (id, cmd) -> applier.apply(id, cmd)));
        System.out.println("STRESS after(service) " + run("s", (id, cmd) -> service.verify(id, cmd)));
        
    }

    private String run(String prefix, BiConsumer<Long, IdentityVerifyCommand> call) throws Exception {
        int other = 0;
        int ok = 0;
        int dup = 0;
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        for (int round = 0; round < ROUNDS; round++) {
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                ids.add(memberRepository.saveAndFlush(aMember().build()).getId());
            }
            IdentityVerifyCommand cmd = new IdentityVerifyCommand(1995, Gender.MALE, prefix + "-" + round);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (Long id : ids) {
                futures.add(executor.submit(() -> {
                    start.await();
                    call.accept(id, cmd);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                try {
                    f.get();
                    ok++;
                } catch (java.util.concurrent.ExecutionException e) {
                    if (e.getCause() instanceof BusinessException) {
                        dup++;
                    } else {
                        other++;
                        if (other == 1) {
                            System.out.println("STRESS cause " + e.getCause());
                            String status = jdbc.queryForObject("SHOW ENGINE INNODB STATUS", (rs, n) -> rs.getString(3));
                            int i = status.indexOf("LATEST DETECTED DEADLOCK");
                            System.out.println("STRESS innodb " + (i < 0 ? "none" : status.substring(i, Math.min(status.length(), i + 6000))));
                        }
                    }
                }
            }
        }
        executor.shutdown();
        return "rounds=" + ROUNDS + " ok=" + ok + " duplicated=" + dup + " otherErrors=" + other;
    }
}
