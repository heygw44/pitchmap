package com.pitchmap.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.domain.MemberException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class MemberLoginServiceIntegrationTest {

    private static final String PASSWORD = "Valid-pass1";
    private static final String IP = "203.0.113.7";
    private static final int ATTEMPTED_EMAIL_MAX_LENGTH = 254;

    @Autowired
    private MemberLoginService memberLoginService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-02] 이메일 형식이 올바르지 않아도 500이 아니라 LOGIN_FAILED로 거부하고 실패를 기록한다")
    void malformedEmailIsRejectedAsLoginFailed() {
        assertThatThrownBy(() -> memberLoginService.login(new LoginCommand("Not-An-Email", PASSWORD, IP)))
                .isInstanceOf(MemberException.class)
                .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.LOGIN_FAILED);

        Integer failures = jdbc.queryForObject(
                "SELECT COUNT(*) FROM login_history WHERE attempted_email = ? AND member_id IS NULL AND success = FALSE",
                Integer.class,
                "not-an-email");
        assertThat(failures).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-02] 이메일이 컬럼 길이보다 길면 잘라서 기록하고 LOGIN_FAILED로 거부한다")
    void overlongEmailIsTruncatedWhenRecorded() {
        String overlong = "x".repeat(ATTEMPTED_EMAIL_MAX_LENGTH + 46);

        assertThatThrownBy(() -> memberLoginService.login(new LoginCommand(overlong, PASSWORD, IP)))
                .isInstanceOf(MemberException.class)
                .hasFieldOrPropertyWithValue("errorCode", MemberErrorCode.LOGIN_FAILED);

        Integer recordedLength =
                jdbc.queryForObject("SELECT MAX(CHAR_LENGTH(attempted_email)) FROM login_history", Integer.class);
        assertThat(recordedLength).isEqualTo(ATTEMPTED_EMAIL_MAX_LENGTH);
    }
}
