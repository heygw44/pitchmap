-- 공식 행사와 선착순 신청, 가짜 결제, 빈자리 알림 신청, 멱등성 기록 테이블 다섯 개다.
-- 시각 컬럼은 V1과 같이 DATETIME(6) UTC이고, 시각은 앱이 Clock으로 정해서 채운다. 상태 값은 VARCHAR로 두고 Java enum이 값을 정한다.
-- 회원과 장소는 지우지 않고 익명화하거나 숨기므로 FK는 모두 기본 동작(RESTRICT)으로 둔다.

-- 관리자가 등록하는 공식 행사. spot_id는 지도의 장소와 연결할 때만 채운다.
-- 앱이 저장하기 전에 범위와 시각 순서를 먼저 검사하지만, 어느 경로로 저장하든 DB가 잘못된 값을 거부하도록 CHECK도 둔다.
-- 신청 가능 여부는 status가 아니라 신청 시작·마감 시각으로 판단하므로, status에는 SCHEDULED와 CANCELED만 둔다.
CREATE TABLE program (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    spot_id BIGINT NULL,
    location_text VARCHAR(255) NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    capacity INT NOT NULL,
    fee INT NOT NULL,
    apply_open_at DATETIME(6) NOT NULL,
    apply_close_at DATETIME(6) NOT NULL,
    payment_deadline_minutes SMALLINT NOT NULL DEFAULT 15,
    overnight BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_program_capacity CHECK (capacity >= 1),
    CONSTRAINT ck_program_fee CHECK (fee >= 0),
    CONSTRAINT ck_program_payment_deadline_minutes CHECK (payment_deadline_minutes >= 1),
    CONSTRAINT ck_program_apply_period CHECK (apply_open_at < apply_close_at),
    CONSTRAINT ck_program_apply_close_start CHECK (apply_close_at <= start_at),
    CONSTRAINT ck_program_period CHECK (start_at < end_at),
    CONSTRAINT fk_program_spot FOREIGN KEY (spot_id) REFERENCES spot (id),
    CONSTRAINT fk_program_created_by FOREIGN KEY (created_by) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 공개 목록은 시작 시각이 이른 순서로 읽고, 시작 시각이 같으면 id 순서로 읽는다.
-- (start_at, id)로 만들면 정렬과 페이지 넘김을 인덱스 순서 그대로 처리한다.
CREATE INDEX idx_program_start_at_id ON program (start_at, id);

-- 회원이 행사에 낸 신청. 취소하거나 만료된 뒤 다시 신청할 수 있어서 같은 회원의 행이 여러 개 남을 수 있다.
-- 그래서 (program_id, member_id) UNIQUE를 두지 않는다. 활성 신청을 회원당 한 건으로 제한하는 방법은 선착순 신청을 구현할 때 정한다.
-- payment_due_at은 신청할 때 정하는 결제 기한이다. cancel_reason은 신청이 취소·만료된 이유다.
CREATE TABLE program_application (
    id BIGINT NOT NULL AUTO_INCREMENT,
    program_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    payment_due_at DATETIME(6) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    canceled_at DATETIME(6) NULL,
    cancel_reason VARCHAR(30) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_program_application_program FOREIGN KEY (program_id) REFERENCES program (id),
    CONSTRAINT fk_program_application_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 남은 자리는 행사별로 결제 대기·확정 신청 수를 세어 구하고, 신청자 목록은 행사 안에서 상태로 거른다.
-- (program_id, status)로 만들면 행사의 신청 행만 찾아 상태별로 센다.
CREATE INDEX idx_program_application_program_status ON program_application (program_id, status);

-- 신청마다 하나뿐인 가짜 결제. 같은 신청을 두 번 결제해도 DB가 UNIQUE로 한 건만 받는다.
CREATE TABLE payment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    program_application_id BIGINT NOT NULL,
    amount INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    paid_at DATETIME(6) NOT NULL,
    refunded_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_program_application UNIQUE (program_application_id),
    CONSTRAINT fk_payment_program_application FOREIGN KEY (program_application_id) REFERENCES program_application (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 정원이 찬 행사에서 자리가 나면 알려 달라는 신청. 회원은 행사마다 한 번만 신청하고, 이 UNIQUE가 DB에서 지킨다.
-- notified_at은 마지막으로 알린 시각이다.
CREATE TABLE program_vacancy_alert (
    id BIGINT NOT NULL AUTO_INCREMENT,
    program_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    notified_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_program_vacancy_alert_program_member UNIQUE (program_id, member_id),
    CONSTRAINT fk_program_vacancy_alert_program FOREIGN KEY (program_id) REFERENCES program (id),
    CONSTRAINT fk_program_vacancy_alert_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 멱등성 키별 처리 결과. 키는 회원 단위로 구분해서 (member_id, idempotency_key)가 기본 키다.
-- request_hash는 같은 키로 다른 요청이 오면 거부하려고 저장한다. response_status와 response_body는 처리가 끝난 뒤에 채운다.
-- 하루가 지난 행을 정리 작업이 지우는데, 그 기준이 되는 created_at이 있다.
CREATE TABLE idempotency_record (
    member_id BIGINT NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_status SMALLINT NULL,
    response_body JSON NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (member_id, idempotency_key),
    CONSTRAINT fk_idempotency_record_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 정리 작업이 만든 지 하루가 지난 행을 created_at 조건으로 찾아 지운다.
CREATE INDEX idx_idempotency_record_created_at ON idempotency_record (created_at);
