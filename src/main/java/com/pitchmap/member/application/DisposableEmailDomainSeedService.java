package com.pitchmap.member.application;

import com.pitchmap.member.infra.DisposableEmailDomainListFile;
import com.pitchmap.member.infra.DisposableEmailDomainMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisposableEmailDomainSeedService {

    private static final int SEED_CHUNK_SIZE = 500;

    private final DisposableEmailDomainListFile listFile;
    private final DisposableEmailDomainMapper mapper;
    private final Clock clock;

    @Transactional
    public int seedPublicDomains() {
        List<String> domains = listFile.readDomains();
        Instant now = Instant.now(clock);
        int inserted = 0;
        for (int from = 0; from < domains.size(); from += SEED_CHUNK_SIZE) {
            int to = Math.min(from + SEED_CHUNK_SIZE, domains.size());
            inserted += mapper.insertPublicIfAbsent(domains.subList(from, to), now);
        }
        log.info("seeded public disposable email domains count={}", inserted);
        return inserted;
    }
}
