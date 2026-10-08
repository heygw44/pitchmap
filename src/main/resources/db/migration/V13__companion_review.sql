-- 완료된 베이스캠프의 멤버끼리 쓰는 동행 후기. 시각 컬럼은 앱이 Clock으로 정해서 채운다.
-- 후기는 쓴 뒤에 바뀌지 않으므로 updated_at을 두지 않는다. hidden_at만 신고 처리로 나중에 채워진다.
-- 같은 베이스캠프에서 같은 상대에게는 한 번만 쓸 수 있다. 동시에 같은 후기가 들어와도 DB가 하나만 받도록 UNIQUE로 막는다.
-- 상대가 나에게 쓴 후기(반대 방향)가 있는지는 이 UNIQUE 인덱스로 찾고, 받은 후기 조회는 reviewee_id의 FK 인덱스를 쓴다.
-- 회원과 베이스캠프는 지우지 않고 숨기거나 익명화하므로 FK는 기본 동작(RESTRICT)으로 둔다.
CREATE TABLE companion_review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    basecamp_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    reviewee_id BIGINT NOT NULL,
    rejoin_wanted BOOLEAN NOT NULL,
    comment VARCHAR(300) NULL,
    hidden_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_companion_review_basecamp_reviewer_reviewee UNIQUE (basecamp_id, reviewer_id, reviewee_id),
    CONSTRAINT ck_companion_review_not_self CHECK (reviewer_id <> reviewee_id),
    CONSTRAINT fk_companion_review_basecamp FOREIGN KEY (basecamp_id) REFERENCES basecamp (id),
    CONSTRAINT fk_companion_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES member (id),
    CONSTRAINT fk_companion_review_reviewee FOREIGN KEY (reviewee_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 후기에 붙은 태그. 한 후기에 같은 태그는 한 번만 붙으므로 (review_id, tag)가 기본키다.
CREATE TABLE companion_review_tag (
    review_id BIGINT NOT NULL,
    tag VARCHAR(30) NOT NULL,
    PRIMARY KEY (review_id, tag),
    CONSTRAINT fk_companion_review_tag_review FOREIGN KEY (review_id) REFERENCES companion_review (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
