-- 회원이 커뮤니티 글에 누르는 좋아요. 시각 컬럼은 앱이 Clock으로 정해서 채운다.
-- 한 회원은 한 글에 좋아요를 한 번만 누를 수 있고, 이 제약은 기본 키 (post_id, member_id)가 맡는다.
-- 좋아요를 취소하면 행을 지운다.
-- 좋아요 수는 저장하지 않고 post_id로 센다. 기본 키의 앞부분이 post_id라서 별도 인덱스는 두지 않는다.
-- member_id에는 FK 인덱스가 생겨서 회원별로도 찾을 수 있다.
-- 글과 회원은 지우지 않고 숨기거나 익명화하므로 FK는 기본 동작(RESTRICT)으로 둔다.
CREATE TABLE community_post_like (
    post_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (post_id, member_id),
    CONSTRAINT fk_community_post_like_post FOREIGN KEY (post_id) REFERENCES community_post (id),
    CONSTRAINT fk_community_post_like_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
