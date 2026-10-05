package com.pitchmap.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.domain.MemberException;
import java.sql.SQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class MemberUniqueConstraintTranslatorTest {

    @Test
    @DisplayName("[F-01][EV-06] 이메일 유니크 제약 위반이면 MEMBER_EMAIL_DUPLICATED")
    void emailConstraintBecomesEmailDuplicated() {
        RuntimeException translated = translate("Duplicate entry 'a@b.com' for key 'member.uk_member_email'");

        assertThat(translated)
                .isInstanceOfSatisfying(
                        MemberException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_EMAIL_DUPLICATED));
    }

    @Test
    @DisplayName("[F-01] 닉네임 유니크 제약 위반이면 MEMBER_NICKNAME_DUPLICATED")
    void nicknameConstraintBecomesNicknameDuplicated() {
        RuntimeException translated = translate("Duplicate entry 'hiker' for key 'member.uk_member_nickname'");

        assertThat(translated)
                .isInstanceOfSatisfying(
                        MemberException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED));
    }

    @Test
    @DisplayName("[F-01] 닉네임이 제약 이름과 같아도 어긴 제약이 닉네임이면 MEMBER_NICKNAME_DUPLICATED")
    void duplicatedValueThatLooksLikeConstraintNameDoesNotConfuseTranslation() {
        RuntimeException translated =
                translate("Duplicate entry 'uk_member_email' for key 'member.uk_member_nickname'");

        assertThat(translated)
                .isInstanceOfSatisfying(
                        MemberException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED));
    }

    @Test
    @DisplayName("[F-01] 값이 어긴 제약 이름 뒤 구절을 흉내 내도 마지막 for key 뒤의 제약으로 판단한다")
    void duplicatedValueThatImitatesKeyClauseDoesNotConfuseTranslation() {
        RuntimeException translated =
                translate("Duplicate entry 'x for key 'member.uk_member_email' y' for key 'member.uk_member_nickname'");

        assertThat(translated)
                .isInstanceOfSatisfying(
                        MemberException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED));
    }

    @Test
    @DisplayName("[F-01] 회원 유니크 제약이 아닌 위반은 받은 예외를 그대로 돌려준다")
    void otherViolationIsReturnedAsIs() {
        DataIntegrityViolationException original = exception("Duplicate entry '1' for key 'other.PRIMARY'");

        assertThat(MemberUniqueConstraintTranslator.translate(original)).isSameAs(original);
    }

    private static RuntimeException translate(String mysqlMessage) {
        return MemberUniqueConstraintTranslator.translate(exception(mysqlMessage));
    }

    private static DataIntegrityViolationException exception(String mysqlMessage) {
        return new DataIntegrityViolationException(
                "could not execute statement", new SQLIntegrityConstraintViolationException(mysqlMessage));
    }
}
