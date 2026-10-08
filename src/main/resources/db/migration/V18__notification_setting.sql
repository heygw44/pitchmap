-- 회원이 알림 종류별로 이메일 수신 여부를 정한 설정이다. type은 notification.type과 같은 값(알림을 만든 이벤트 종류 이름)이다.
-- 행이 없는 종류는 앱이 정한 기본값(이메일 받기)을 따른다. 그래서 이메일을 끄거나 켠 종류만 행으로 저장한다.
-- 회원은 지우지 않고 익명화하므로 FK는 기본 동작(RESTRICT)으로 둔다.
--
-- (member_id, type)이 기본 키라서 한 회원은 종류마다 한 행만 갖는다. 메일 발송 대상을 고르는 SQL이 이 키로 설정을 찾는다.
CREATE TABLE notification_setting (
    member_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    email_enabled BOOLEAN NOT NULL,
    PRIMARY KEY (member_id, type),
    CONSTRAINT fk_notification_setting_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
