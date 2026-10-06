-- 회원이 본인확인을 마친 기록. 회원당 한 행이다. 시각 컬럼은 앱이 Clock으로 정해서 채운다.
-- 원본 CI(본인확인기관이 사람마다 하나씩 주는 고유 식별값)와 이름, 생년월일 전체, 전화번호는 저장하지 않는다.
-- 출생연도와 성별은 성인 판정과 동성 조건에 쓰려고 남기고, CI는 비밀 키로 만든 HMAC 해시(소문자 16진수 64자)만 저장한다.
-- 같은 회원이 두 번, 같은 사람이 두 계정으로 본인확인하지 못하도록 member_id와 ci_hash에 UNIQUE를 둔다. 동시에 들어와도 DB가 하나만 받는다.
-- 탈퇴하면 출생연도와 성별을 NULL로 바꾸고 ci_retained_until에 보관 기한을 적어 CI 해시만 그 기한까지 남긴다. 그래서 updated_at이 있다.
-- 회원은 지우지 않고 익명화하므로 FK는 기본 동작(RESTRICT)으로 둔다.
CREATE TABLE identity_verification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    birth_year SMALLINT NULL,
    gender VARCHAR(10) NULL,
    ci_hash CHAR(64) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    verified_at DATETIME(6) NOT NULL,
    ci_retained_until DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_identity_verification_member UNIQUE (member_id),
    CONSTRAINT uk_identity_verification_ci_hash UNIQUE (ci_hash),
    CONSTRAINT fk_identity_verification_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
