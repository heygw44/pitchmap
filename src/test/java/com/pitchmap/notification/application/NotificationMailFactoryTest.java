package com.pitchmap.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.mail.MailMessage;
import com.pitchmap.notification.infra.NotificationMailRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationMailFactoryTest {

    @Test
    @DisplayName("[F-20] 제목에 서비스 이름을 붙이고, 링크가 있으면 본문 아래에 전체 주소를 붙인다")
    void withLink() {
        NotificationMailRow row = new NotificationMailRow(1L, "a@example.com", "합류 승인", "승인됐습니다.", "/basecamps/3");

        MailMessage mail = NotificationMailFactory.create(row, "https://pitchmap.example");

        assertThat(mail.to()).isEqualTo("a@example.com");
        assertThat(mail.subject()).isEqualTo("[피치맵] 합류 승인");
        assertThat(mail.body()).isEqualTo("승인됐습니다.\n\n자세히 보기: https://pitchmap.example/basecamps/3");
    }

    @Test
    @DisplayName("[F-20] 링크가 없으면 본문만 보낸다")
    void withoutLink() {
        NotificationMailRow row = new NotificationMailRow(1L, "a@example.com", "제재 확정", "정지됐습니다.", null);

        MailMessage mail = NotificationMailFactory.create(row, "https://pitchmap.example");

        assertThat(mail.body()).isEqualTo("정지됐습니다.");
    }

    @Test
    @DisplayName("[F-20] 기본 주소 끝의 슬래시는 지우고 붙인다")
    void stripsTrailingSlashes() {
        NotificationMailRow row = new NotificationMailRow(1L, "a@example.com", "제목", "본문", "/basecamps/3");

        MailMessage mail = NotificationMailFactory.create(row, "https://pitchmap.example//");

        assertThat(mail.body()).endsWith("https://pitchmap.example/basecamps/3");
    }

    @Test
    @DisplayName("[F-20] 메일 대상 행의 문자열 표현에는 이메일이 드러나지 않는다")
    void toStringHidesEmail() {
        NotificationMailRow row = new NotificationMailRow(1L, "secret@example.com", "제목", "본문", null);

        assertThat(row.toString()).doesNotContain("secret@example.com");
    }
}
