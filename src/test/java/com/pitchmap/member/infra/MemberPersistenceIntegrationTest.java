package com.pitchmap.member.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@IntegrationTest
class MemberPersistenceIntegrationTest {

    @Autowired
    private MemberJpaRepository memberRepository;

    @Test
    @DisplayName("[F-01] 저장한 회원의 이메일과 닉네임이 존재하는 것으로 조회된다")
    void savedMember_existsByEmailAndNickname() {
        // given
        String email = TestSequence.email();
        String nickname = TestSequence.nickname();
        memberRepository.saveAndFlush(
                new MemberBuilder().email(email).nickname(nickname).build());

        // when & then
        assertThat(memberRepository.existsByEmail(email)).isTrue();
        assertThat(memberRepository.existsByNickname(nickname)).isTrue();
        assertThat(memberRepository.existsByEmail(TestSequence.email())).isFalse();
        assertThat(memberRepository.existsByNickname(TestSequence.nickname())).isFalse();
    }

    @Test
    @DisplayName("[F-01][ID-01] 같은 이메일로 두 번 저장하면 uk_member_email 제약에 걸린다")
    void duplicateEmail_violatesUniqueIndex() {
        // given
        String email = TestSequence.email();
        memberRepository.saveAndFlush(new MemberBuilder()
                .email(email)
                .nickname(TestSequence.nickname())
                .build());
        Member duplicate = new MemberBuilder()
                .email(email)
                .nickname(TestSequence.nickname())
                .build();

        // when & then
        assertThatThrownBy(() -> memberRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_member_email");
    }

    @Test
    @DisplayName("[F-01][ID-01] 같은 닉네임으로 두 번 저장하면 uk_member_nickname 제약에 걸린다")
    void duplicateNickname_violatesUniqueIndex() {
        // given
        String nickname = TestSequence.nickname();
        memberRepository.saveAndFlush(new MemberBuilder()
                .email(TestSequence.email())
                .nickname(nickname)
                .build());
        Member duplicate = new MemberBuilder()
                .email(TestSequence.email())
                .nickname(nickname)
                .build();

        // when & then
        assertThatThrownBy(() -> memberRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_member_nickname");
    }
}
