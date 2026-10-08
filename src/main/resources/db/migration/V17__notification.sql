-- 회원에게 보여 주는 알림함 한 줄이다. 아웃박스 이벤트를 받는 처리기가 받는 사람마다 한 행씩 만든다.
-- type은 알림을 만든 이벤트 종류 이름과 같다. link는 화면 안의 경로이고, 연결할 화면이 없으면 NULL이다.
-- 회원은 지우지 않고 익명화하므로 FK는 기본 동작(RESTRICT)으로 둔다.
--
-- dedup_key는 "{이벤트 ID}:{받는 회원 ID}"이다. 아웃박스는 같은 이벤트를 적어도 한 번 전달하므로 처리기가 같은 이벤트를 두 번 받을 수 있다.
-- 이 컬럼의 유니크 제약이 같은 이벤트로 같은 회원에게 알림이 두 번 생기는 것을 DB에서 막는다.
--
-- read_at이 NULL이면 안 읽은 알림이다. email_sent_at은 같은 알림을 메일로도 보낸 시각이고, NULL이면 메일을 보내지 않았다.
-- 시각 컬럼은 V1과 같이 DATETIME(6) UTC이고, 시각은 앱이 Clock으로 정해서 채운다.
CREATE TABLE notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(100) NOT NULL,
    body VARCHAR(500) NOT NULL,
    link VARCHAR(255) NULL,
    dedup_key VARCHAR(100) NOT NULL,
    read_at DATETIME(6) NULL,
    email_sent_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_dedup_key UNIQUE (dedup_key),
    CONSTRAINT fk_notification_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 알림 목록은 회원별로 id 내림차순(최신순)으로 읽고, 안 읽은 알림 수는 회원별로 센다.
-- (member_id, id)로 만들면 한 회원의 행만 id 순서대로 읽어서 정렬을 건너뛴다.
-- 안 읽은 알림 수는 이 인덱스로 회원의 행을 찾은 뒤 read_at이 NULL인 행을 센다.
CREATE INDEX idx_notification_member_id_id ON notification (member_id, id);
