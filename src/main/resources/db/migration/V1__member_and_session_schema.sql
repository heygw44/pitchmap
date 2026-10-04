-- 시각 컬럼은 DATETIME(6) UTC이고 DB 기본값·ON UPDATE를 두지 않는다.
-- 테스트에서 시계를 옮길 수 있도록 시각은 앱이 Clock으로 정해서 채운다.

-- 탈퇴해도 행은 남기고 익명화한다.
-- email은 탈퇴 시 NULL로 지우는데, UNIQUE는 NULL 여러 개를 허용한다.
CREATE TABLE member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NULL,
    password_hash VARCHAR(100) NULL,
    nickname VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    role VARCHAR(20) NOT NULL,
    self_age_group VARCHAR(10) NULL,
    self_gender VARCHAR(10) NULL,
    email_verified_at DATETIME(6) NULL,
    suspended_until DATETIME(6) NULL,
    withdrawn_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_email UNIQUE (email),
    CONSTRAINT uk_member_nickname UNIQUE (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 인증 코드는 회원 생명주기를 따른다. 미인증 계정 정리가 member 행만 지워도 되도록 CASCADE.
-- created_at이 발송 시각이며 재발송 간격 계산에 쓴다.
CREATE TABLE email_verification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    code_hash CHAR(64) NOT NULL,
    request_ip VARCHAR(45) NOT NULL,
    attempt_count TINYINT NOT NULL DEFAULT 0,
    expires_at DATETIME(6) NOT NULL,
    verified_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_email_verification_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 토큰도 회원 생명주기를 따르므로 CASCADE.
CREATE TABLE password_reset_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_token_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_token_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 접속 기록은 3개월 보관해야 하므로 회원 행이 지워져도 남기려고 SET NULL.
-- 없는 이메일로 시도하면 member_id가 NULL이다. 추가만 하는 테이블이라 updated_at이 없다.
CREATE TABLE login_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NULL,
    attempted_email VARCHAR(254) NOT NULL,
    ip VARCHAR(45) NOT NULL,
    success BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_login_history_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 일회용 이메일 도메인 차단 목록. 도메인이 자연키다.
CREATE TABLE disposable_email_domain (
    domain VARCHAR(253) NOT NULL,
    source VARCHAR(10) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Spring Session 표준 스키마를 그대로 옮긴 것이다. 세션 저장소가 이 스키마를 기대하므로 고치지 않는다.
-- PRINCIPAL_NAME 인덱스로 회원별 세션을 일괄 삭제한다.
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INT NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES BLOB NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;
