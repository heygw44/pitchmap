-- 회원이 커뮤니티 글이나 댓글에 남기는 신고. 시각 컬럼은 앱이 Clock으로 정해서 채운다.
-- 대상이 글과 댓글 두 테이블에 걸쳐 있어서 target_id에는 FK를 걸지 않는다. 대상이 있는지는 앱이 저장 전에 확인한다.
-- 한 회원은 같은 글이나 댓글을 한 번만 신고할 수 있고, 이 제약은 유니크 키 (target_type, target_id, reporter_id)가 맡는다.
-- reviewed_at이 NULL이면 관리자가 아직 검토하지 않은 신고다. 관리자가 글이나 댓글을 복구하면서 검토 전 신고에 이 시각을 채운다.
-- 검토 전 신고의 수는 (target_type, target_id)로 센다. 유니크 키의 앞부분이 이 두 컬럼이라서 별도 인덱스는 두지 않는다.
-- 검토를 마친 신고도 행을 지우지 않는다. 그래야 같은 회원이 같은 대상을 다시 신고하지 못한다.
-- 회원은 지우지 않고 익명화하므로 FK는 기본 동작(RESTRICT)으로 둔다.
CREATE TABLE community_report (
    id BIGINT NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(10) NOT NULL,
    target_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    reason VARCHAR(20) NOT NULL,
    content VARCHAR(1000) NULL,
    reviewed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_community_report_target_reporter UNIQUE (target_type, target_id, reporter_id),
    CONSTRAINT fk_community_report_reporter FOREIGN KEY (reporter_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
