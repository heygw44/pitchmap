package com.pitchmap.member.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.application.DisposableEmailDomainSeedService;
import com.pitchmap.member.domain.DisposableEmailDomain;
import com.pitchmap.member.domain.DisposableEmailDomainSource;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class DisposableEmailDomainSeedIntegrationTest {

    private static final int MIN_EXPECTED_DOMAINS = 1000;

    @Autowired
    private DisposableEmailDomainSeedService seedService;

    @Autowired
    private DisposableEmailDomainListFile listFile;

    @Autowired
    private DisposableEmailDomainJpaRepository repository;

    @Test
    @DisplayName("[F-01][EV-01] 빈 테이블에 공개 목록을 PUBLIC으로 넣고, 다시 실행하면 0건이다")
    void seedPublicDomains_isIdempotent() {
        // given
        repository.deleteAll();
        List<String> domains = listFile.readDomains();
        assertThat(domains).hasSizeGreaterThan(MIN_EXPECTED_DOMAINS);

        // when
        int first = seedService.seedPublicDomains();
        long countAfterFirst = repository.count();
        int second = seedService.seedPublicDomains();

        // then
        assertThat(first).isEqualTo(domains.size());
        assertThat(countAfterFirst).isEqualTo(domains.size());
        assertThat(repository.findById(domains.get(0)))
                .get()
                .extracting(DisposableEmailDomain::getSource)
                .isEqualTo(DisposableEmailDomainSource.PUBLIC);
        assertThat(second).isZero();
        assertThat(repository.count()).isEqualTo(countAfterFirst);
    }

    @Test
    @DisplayName("[F-01][EV-01] 관리자가 등록한 도메인은 공개 목록에 있어도 ADMIN으로 남는다")
    void seedPublicDomains_keepsAdminRow() {
        // given
        repository.deleteAll();
        String domain = listFile.readDomains().get(0);
        repository.saveAndFlush(
                DisposableEmailDomain.of(domain, DisposableEmailDomainSource.ADMIN, MutableClock.DEFAULT_INSTANT));

        // when
        seedService.seedPublicDomains();

        // then
        assertThat(repository.findById(domain))
                .get()
                .extracting(DisposableEmailDomain::getSource)
                .isEqualTo(DisposableEmailDomainSource.ADMIN);
    }

    @Test
    @DisplayName("[F-01][EV-01] 후보 도메인 중 하나라도 있으면 existsByDomainIn이 true다")
    void existsByDomainIn_matchesAnyCandidate() {
        // given
        repository.deleteAll();
        repository.saveAndFlush(DisposableEmailDomain.of(
                "mailinator.com", DisposableEmailDomainSource.PUBLIC, MutableClock.DEFAULT_INSTANT));

        // when & then
        assertThat(repository.existsByDomainIn(List.of("a.mailinator.com", "mailinator.com")))
                .isTrue();
        assertThat(repository.existsByDomainIn(List.of("example.org", "gmail.com")))
                .isFalse();
    }
}
