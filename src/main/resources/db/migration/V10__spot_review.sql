-- 회원이 다녀온 장소에 남기는 후기와 평점. 시각 컬럼은 앱이 Clock으로 정해서 채운다.
-- 평균 평점과 후기 수는 저장하지 않고, 장소 상세가 이 테이블을 읽어 조회할 때 계산한다.
-- 한 회원은 같은 장소를 방문일마다 한 번만 후기로 남길 수 있다. 동시에 같은 후기가 들어와도 DB가 하나만 받도록 UNIQUE로 막는다.
-- 이 UNIQUE 인덱스가 spot_id로 시작하므로 장소별 후기 조회에 따로 인덱스를 두지 않는다.
-- 장소와 회원은 지우지 않고 숨기거나 익명화하므로 FK는 기본 동작(RESTRICT)으로 둔다. 후기는 작성자가 직접 지우면 행을 삭제한다.
CREATE TABLE spot_review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    spot_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    visited_date DATE NOT NULL,
    rating TINYINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_spot_review_spot_member_visited_date UNIQUE (spot_id, member_id, visited_date),
    CONSTRAINT ck_spot_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_spot_review_spot FOREIGN KEY (spot_id) REFERENCES spot (id),
    CONSTRAINT fk_spot_review_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
