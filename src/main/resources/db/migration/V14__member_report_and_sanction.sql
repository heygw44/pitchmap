-- 회원 신고와 동행 후기 신고. 시각 컬럼은 앱이 Clock으로 정해서 채운다.
-- 신고는 접수(RECEIVED) 뒤에 관리자가 검토하고 처리하므로 상태와 함께 바뀌는 updated_at을 둔다.
-- 같은 신고자가 같은 베이스캠프에서 같은 상대를 같은 종류(회원 또는 후기)로 두 번 신고하지 못한다. 동시에 같은 신고가 들어와도 DB가 UNIQUE로 하나만 받는다.
-- 한 베이스캠프에서 같은 상대에게 받는 후기는 하나뿐이라, 후기 신고도 이 조합으로 한 번이면 충분하다.
-- 회원과 베이스캠프와 후기는 지우지 않고 숨기거나 익명화하므로 FK는 기본 동작(RESTRICT)으로 둔다.
CREATE TABLE member_report (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_id BIGINT NOT NULL,
    target_member_id BIGINT NOT NULL,
    basecamp_id BIGINT NOT NULL,
    kind VARCHAR(10) NOT NULL,
    companion_review_id BIGINT NULL,
    type VARCHAR(30) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    urgent BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL,
    handled_by BIGINT NULL,
    handled_at DATETIME(6) NULL,
    result_note VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_report_reporter_target_basecamp_kind UNIQUE (reporter_id, target_member_id, basecamp_id, kind),
    CONSTRAINT ck_member_report_not_self CHECK (reporter_id <> target_member_id),
    CONSTRAINT fk_member_report_reporter FOREIGN KEY (reporter_id) REFERENCES member (id),
    CONSTRAINT fk_member_report_target FOREIGN KEY (target_member_id) REFERENCES member (id),
    CONSTRAINT fk_member_report_basecamp FOREIGN KEY (basecamp_id) REFERENCES basecamp (id),
    CONSTRAINT fk_member_report_companion_review FOREIGN KEY (companion_review_id) REFERENCES companion_review (id),
    CONSTRAINT fk_member_report_handled_by FOREIGN KEY (handled_by) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 회원에게 내린 제재 이력. 성희롱·위협 신고가 들어오면 서버가 만드는 72시간 임시 정지(created_by가 NULL)도 한 행으로 남는다.
-- 같은 회원에게 긴급 신고가 여러 건 오면 신고마다 임시 정지 행이 하나씩 생긴다.
-- 회원의 현재 제재와 단계를 계산할 때 (member_id, status)로 찾으므로 이 조합에 인덱스를 둔다.
-- report_id는 근거 신고이고, 신고 없이 내리는 제재를 위해 NULL을 허용한다.
CREATE TABLE sanction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    level TINYINT NULL,
    report_id BIGINT NULL,
    reason VARCHAR(500) NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NULL,
    status VARCHAR(20) NOT NULL,
    created_by BIGINT NULL,
    lifted_by BIGINT NULL,
    lifted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_sanction_member_status (member_id, status),
    CONSTRAINT fk_sanction_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_sanction_report FOREIGN KEY (report_id) REFERENCES member_report (id),
    CONSTRAINT fk_sanction_created_by FOREIGN KEY (created_by) REFERENCES member (id),
    CONSTRAINT fk_sanction_lifted_by FOREIGN KEY (lifted_by) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
