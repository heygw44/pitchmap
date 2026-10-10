-- 커뮤니티 글에 붙이는 이미지. 시각 컬럼은 앱이 Clock으로 정해서 채운다.
-- 서버는 업로드 URL을 발급할 때 post_id가 NULL인 행을 먼저 만든다. 글에 붙이면 post_id와 display_order를 채우고,
-- 글을 고치면서 뺀 이미지는 두 컬럼을 다시 NULL로 되돌린다. 글을 지워도(status만 DELETED) 이미지는 글에 붙은 채로 둔다.
-- 정리 작업은 post_id가 NULL이고 만든 지 24시간이 지난 행을 저장소 객체와 함께 지운다.
-- 정리 작업용 인덱스는 아직 두지 않는다. 인덱스는 실행 계획을 확인한 뒤에 필요한 것만 추가한다.
-- post_id의 FK가 만드는 인덱스를 글별 이미지 조회에 쓴다.
-- object_key는 저장소 안의 객체 경로이고 서버가 만든다. 같은 경로를 두 행이 가리키지 못하게 유니크로 둔다.
-- size_bytes는 업로드 URL 서명에 넣은 크기이고, display_order는 글 안 순서(0~4)다.
-- 회원과 글은 지우지 않고 숨기거나 익명화하므로 FK는 기본 동작(RESTRICT)으로 둔다.
CREATE TABLE community_image (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    post_id BIGINT NULL,
    object_key VARCHAR(255) NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    size_bytes INT NOT NULL,
    display_order TINYINT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_community_image_object_key UNIQUE (object_key),
    CONSTRAINT fk_community_image_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_community_image_post FOREIGN KEY (post_id) REFERENCES community_post (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
