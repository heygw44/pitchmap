-- 관리자가 바꾼 내용을 누가, 언제, 무엇에 했는지 남기는 감사 로그다. 관리자 요청 하나마다 한 행을 남기고, 조치와 같은 트랜잭션에서 기록한다.
-- 그래서 조치가 롤백되면 기록도 함께 사라진다.
-- target_id는 신고, 제재, 공공데이터 실행 기록처럼 대상 테이블이 action마다 달라서 FK를 걸지 않는다. admin_id만 회원을 가리킨다.
-- 회원은 지우지 않고 익명화하므로 FK는 기본 동작(RESTRICT)으로 둔다.
-- detail에는 상태 전후와 제재 종류처럼 식별 정보만 넣는다. 신고 내용, 메모, 제재 사유 원문은 넣지 않는다.
-- 시각 컬럼은 V1과 같이 DATETIME(6) UTC이고, 시각은 앱이 Clock으로 정해서 채운다.
CREATE TABLE admin_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id BIGINT NOT NULL,
    detail JSON NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_admin_audit_log_admin FOREIGN KEY (admin_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 감사 로그 목록은 기간(created_at) 조건으로 거르고 id 내림차순으로 읽는다.
-- (created_at, id)로 만들면 기간 범위를 인덱스로 찾을 수 있다.
-- adminId, targetType 필터는 기간으로 줄인 행에서 거른다. 실행계획을 본 뒤 모자라면 다음 마이그레이션으로 조정한다.
CREATE INDEX idx_admin_audit_log_created ON admin_audit_log (created_at, id);
