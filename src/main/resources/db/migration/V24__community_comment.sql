-- 커뮤니티 글에 회원이 다는 댓글과 답글. 시각 컬럼은 앱이 Clock으로 정해서 채운다.
-- status는 ACTIVE, PENDING_REVIEW, HIDDEN, DELETED 중 하나다. 앱이 값을 검사하고, 목록에는 ACTIVE인 댓글만 내용을 보여 준다.
-- 작성자가 댓글을 지워도 행은 남기고 status를 DELETED로 바꾸며 content는 NULL로 지운다. 이후에 만들 신고 기록과 관리자 감사 로그가 댓글을 가리키기 때문이다.
-- 답글은 한 단계만 허용한다. 답글의 parent_id는 같은 글의 ACTIVE 최상위 댓글을 가리켜야 하고, 이 조건은 DB가 아니라 앱이 검사한다.
-- 글별 목록용 인덱스는 아직 두지 않는다. post_id FK 인덱스에는 InnoDB가 기본 키(id)를 함께 담으므로 (post_id, id) 순서로 읽을 수 있다.
-- parent_id에도 FK 인덱스가 생겨서 답글을 부모 ID로 찾을 수 있다. 인덱스는 실행 계획을 확인한 뒤에 필요한 것만 추가한다.
-- 글과 회원은 지우지 않고 숨기거나 익명화하므로 FK는 기본 동작(RESTRICT)으로 둔다.
CREATE TABLE community_comment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    content VARCHAR(1000) NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_community_comment_post FOREIGN KEY (post_id) REFERENCES community_post (id),
    CONSTRAINT fk_community_comment_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_community_comment_parent FOREIGN KEY (parent_id) REFERENCES community_comment (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
