package com.pitchmap.member.infra;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.domain.PasswordResetPolicy;
import com.pitchmap.member.domain.PasswordResetToken;
import com.pitchmap.member.domain.ResetToken;
import java.security.SecureRandom;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class PasswordResetTokenMapperIntegrationTest {

    private static final Instant ISSUED_AT = MutableClock.DEFAULT_INSTANT;
    private static final Instant EXPIRES_AT = ISSUED_AT.plus(PasswordResetPolicy.TOKEN_VALIDITY);

    private final SecureRandom random = new SecureRandom();

    @Autowired
    private PasswordResetTokenMapper mapper;

    @Autowired
    private PasswordResetTokenJpaRepository tokenRepository;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("[F-02][PW-02] 처음 쓰는 토큰은 markUsed가 1을 돌려주고 사용 시각을 기록한다")
    void markUsedMarksFreshToken() {
        // given
        PasswordResetToken token = issueFor(newMemberId());
        assertThat(isUsed(token.getId())).isFalse();

        // when
        int updated = mapper.markUsed(token.getId(), ISSUED_AT.plusSeconds(60));

        // then
        assertThat(updated).isEqualTo(1);
        assertThat(isUsed(token.getId())).isTrue();
    }

    @Test
    @DisplayName("[F-02][PW-02] 이미 쓴 토큰은 markUsed가 0을 돌려준다")
    void markUsedReturnsZeroOnSecondCall() {
        // given
        PasswordResetToken token = issueFor(newMemberId());
        assertThat(mapper.markUsed(token.getId(), ISSUED_AT.plusSeconds(60))).isEqualTo(1);

        // when
        int updated = mapper.markUsed(token.getId(), ISSUED_AT.plusSeconds(61));

        // then
        assertThat(updated).isZero();
    }

    @Test
    @DisplayName("[F-02][PW-02] 만료 1초 전에는 1, 만료 시각 정각과 그 뒤에는 0을 돌려준다")
    void markUsedHonorsExpiryBoundary() {
        // given
        PasswordResetToken beforeExpiry = issueFor(newMemberId());
        PasswordResetToken atExpiry = issueFor(newMemberId());
        PasswordResetToken afterExpiry = issueFor(newMemberId());

        // when
        int before = mapper.markUsed(beforeExpiry.getId(), EXPIRES_AT.minusSeconds(1));
        int at = mapper.markUsed(atExpiry.getId(), EXPIRES_AT);
        int after = mapper.markUsed(afterExpiry.getId(), EXPIRES_AT.plusSeconds(1));

        // then
        assertThat(before).isEqualTo(1);
        assertThat(at).isZero();
        assertThat(after).isZero();
        assertThat(isUsed(beforeExpiry.getId())).isTrue();
        assertThat(isUsed(atExpiry.getId())).isFalse();
        assertThat(isUsed(afterExpiry.getId())).isFalse();
    }

    @Test
    @DisplayName("[F-02][PW-02] 사용 시각이 있는 토큰은 저장소에서 다시 읽어도 쓸 수 없다")
    void usedTokenIsNotUsableWhenReloaded() {
        // given
        PasswordResetToken token = issueFor(newMemberId());
        Instant now = ISSUED_AT.plusSeconds(60);
        assertThat(token.isUsable(now)).isTrue();
        mapper.markUsed(token.getId(), now);

        // when
        PasswordResetToken reloaded =
                tokenRepository.findByTokenHash(token.getTokenHash()).orElseThrow();

        // then
        assertThat(reloaded.getUsedAt()).isNotNull();
        assertThat(reloaded.isUsable(now)).isFalse();
    }

    @Test
    @DisplayName("[F-02][PW-02] invalidateUnusedTokens는 그 회원의 쓰지 않은 토큰만 사용 처리하고 바꾼 행 수를 돌려준다")
    void invalidateUnusedTokensTouchesOnlyUnusedRowsOfThatMember() {
        // given
        long memberId = newMemberId();
        long otherMemberId = newMemberId();
        PasswordResetToken unusedA = issueFor(memberId);
        PasswordResetToken unusedB = issueFor(memberId);
        PasswordResetToken alreadyUsed = issueFor(memberId);
        PasswordResetToken othersToken = issueFor(otherMemberId);
        mapper.markUsed(alreadyUsed.getId(), ISSUED_AT.plusSeconds(10));
        Instant usedAtBefore = usedAtOf(alreadyUsed.getId());

        // when
        int invalidated = mapper.invalidateUnusedTokens(memberId, ISSUED_AT.plusSeconds(120));

        // then
        assertThat(invalidated).isEqualTo(2);
        assertThat(isUsed(unusedA.getId())).isTrue();
        assertThat(isUsed(unusedB.getId())).isTrue();
        assertThat(usedAtOf(alreadyUsed.getId())).isEqualTo(usedAtBefore);
        assertThat(isUsed(othersToken.getId())).isFalse();
        assertThat(mapper.invalidateUnusedTokens(memberId, ISSUED_AT.plusSeconds(121)))
                .isZero();
    }

    @Test
    @DisplayName("[F-02][PW-02] invalidateUnusedTokens는 만료된 쓰지 않은 행도 사용 처리한다")
    void invalidateUnusedTokensIncludesExpiredRows() {
        // given
        long memberId = newMemberId();
        PasswordResetToken token = issueFor(memberId);

        // when
        int invalidated = mapper.invalidateUnusedTokens(memberId, EXPIRES_AT.plusSeconds(1));

        // then
        assertThat(invalidated).isEqualTo(1);
        assertThat(isUsed(token.getId())).isTrue();
    }

    @Test
    @DisplayName("[F-02][PW-02] 같은 token_hash를 두 번 저장하면 유니크 제약을 어긴다")
    void duplicateTokenHashViolatesUniqueConstraint() {
        // given
        long memberId = newMemberId();
        String hash = ResetToken.hash(ResetToken.generate(random).value());
        tokenRepository.saveAndFlush(PasswordResetToken.issue(memberId, hash, ISSUED_AT));

        // then
        assertThatThrownBy(() -> tokenRepository.saveAndFlush(PasswordResetToken.issue(memberId, hash, ISSUED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long newMemberId() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    private PasswordResetToken issueFor(long memberId) {
        String hash = ResetToken.hash(ResetToken.generate(random).value());
        return tokenRepository.saveAndFlush(PasswordResetToken.issue(memberId, hash, ISSUED_AT));
    }

    private boolean isUsed(long id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT used_at IS NOT NULL FROM password_reset_token WHERE id = ?", Boolean.class, id));
    }

    private Instant usedAtOf(long id) {
        return tokenRepository.findById(id).orElseThrow().getUsedAt();
    }
}
