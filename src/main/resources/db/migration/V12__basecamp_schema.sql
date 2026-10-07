-- 같은 날 같은 장소에서 1박 이상 함께 야영하는 모임(베이스캠프)과 합류 신청, 멤버 테이블 세 개다.
-- 시각 컬럼은 앱이 Clock으로 정해서 채운다. 상태 값은 VARCHAR로 두고 Java enum이 값을 정한다.
-- 장소와 회원은 지우지 않고 숨기거나 익명화하므로 세 테이블의 FK는 모두 기본 동작(RESTRICT)으로 둔다.
-- 날짜 범위, 신뢰 단계 값, 연령대 상하한처럼 한 행 안에서 판단할 수 있는 규칙 중 정원 말고는 앱이 저장하기 전에 검사한다.

-- 캠프 리더가 여는 베이스캠프. 장소는 경고 박지가 아닌 곳만 고를 수 있고, 이 검사도 앱이 한다.
-- 정원은 캠프 리더를 포함해 2~6명이다. 앱이 먼저 검사하지만, 어느 경로로 저장하든 DB가 범위 밖 값을 거부하도록 CHECK도 둔다.
-- 합류 조건(최소 신뢰 단계, 연령대, 동성만)은 NULL이면 조건이 없다는 뜻이다.
-- 연락 수단은 확정된 멤버에게만 보여 주므로 응답을 만들 때 서버가 공개 여부를 가린다.
CREATE TABLE basecamp (
    id BIGINT NOT NULL AUTO_INCREMENT,
    leader_id BIGINT NOT NULL,
    spot_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    capacity TINYINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    closed_reason VARCHAR(20) NULL,
    min_trust_level TINYINT NULL,
    age_group_min TINYINT NULL,
    age_group_max TINYINT NULL,
    same_gender_only BOOLEAN NOT NULL DEFAULT FALSE,
    required_gender VARCHAR(10) NULL,
    contact_info VARCHAR(255) NULL,
    confirmed_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    canceled_at DATETIME(6) NULL,
    cancel_reason VARCHAR(30) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_basecamp_capacity CHECK (capacity BETWEEN 2 AND 6),
    CONSTRAINT fk_basecamp_leader FOREIGN KEY (leader_id) REFERENCES member (id),
    CONSTRAINT fk_basecamp_spot FOREIGN KEY (spot_id) REFERENCES spot (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 회원이 베이스캠프에 보내는 합류 신청. 한 회원은 한 베이스캠프에 신청 행을 하나만 가진다.
-- 신청자가 취소한 뒤 다시 신청하면 서버가 새 행을 만들지 않고 같은 행을 대기 상태로 되돌린다.
-- 거절된 행이 남아 있으면 그 회원은 같은 베이스캠프에 다시 신청할 수 없다. 동시에 같은 신청이 들어와도 DB가 UNIQUE로 하나만 받는다.
-- 이 UNIQUE 인덱스가 basecamp_id로 시작하므로 베이스캠프별 신청 조회에 따로 인덱스를 두지 않는다.
CREATE TABLE basecamp_application (
    id BIGINT NOT NULL AUTO_INCREMENT,
    basecamp_id BIGINT NOT NULL,
    applicant_id BIGINT NOT NULL,
    message VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL,
    decided_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_basecamp_application_basecamp_applicant UNIQUE (basecamp_id, applicant_id),
    CONSTRAINT fk_basecamp_application_basecamp FOREIGN KEY (basecamp_id) REFERENCES basecamp (id),
    CONSTRAINT fk_basecamp_application_member FOREIGN KEY (applicant_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 승인되어 베이스캠프에 들어온 멤버. 캠프 리더도 한 행으로 들어 있다. 현재 인원은 상태가 ACTIVE인 행의 수다.
-- 신청과 멤버는 생명주기가 달라서 테이블을 나눴다. 신청은 승인되면 끝나지만, 멤버는 그 뒤에도 탈퇴·강퇴와 후기 자격으로 이어진다.
-- 탈퇴하거나 강퇴된 행을 지우지 않고 남기므로, 그 회원은 같은 베이스캠프에 다시 들어올 수 없다. 이 규칙은 UNIQUE가 DB에서 지킨다.
-- 이 UNIQUE 인덱스가 basecamp_id로 시작하므로 베이스캠프별 멤버 조회에 따로 인덱스를 두지 않는다.
CREATE TABLE basecamp_member (
    id BIGINT NOT NULL AUTO_INCREMENT,
    basecamp_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    role VARCHAR(10) NOT NULL,
    status VARCHAR(10) NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    left_at DATETIME(6) NULL,
    early_leave BOOLEAN NOT NULL DEFAULT FALSE,
    kick_reason VARCHAR(30) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_basecamp_member_basecamp_member UNIQUE (basecamp_id, member_id),
    CONSTRAINT fk_basecamp_member_basecamp FOREIGN KEY (basecamp_id) REFERENCES basecamp (id),
    CONSTRAINT fk_basecamp_member_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
