-- 회원이 커뮤니티 게시판에 쓰는 글. 시각 컬럼은 앱이 Clock으로 정해서 채운다.
-- status는 ACTIVE, PENDING_REVIEW, HIDDEN, DELETED 중 하나다. 앱이 값을 검사하고, 목록과 상세는 ACTIVE인 글만 보여 준다.
-- 작성자가 글을 지워도 행은 남기고 status만 DELETED로 바꾼다. 이후에 만들 신고 기록과 관리자 감사 로그가 글을 가리키기 때문이다.
-- 목록은 id 내림차순(최신순)으로 읽고, spot_id에는 FK가 만드는 인덱스가 있다. 그래서 목록용 인덱스는 아직 두지 않는다.
-- 인덱스는 실행 계획을 확인한 뒤에 필요한 것만 추가한다.
-- 회원과 장소는 지우지 않고 숨기거나 익명화하므로 FK는 기본 동작(RESTRICT)으로 둔다.
-- spot_id는 글에 연결한 장소(선택)다. 연결한 장소가 나중에 숨겨져도 글은 그대로 두고, 응답에서 장소 정보만 뺀다.
CREATE TABLE community_post (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    category VARCHAR(20) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    spot_id BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_community_post_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_community_post_spot FOREIGN KEY (spot_id) REFERENCES spot (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
