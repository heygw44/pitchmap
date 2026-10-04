-- 다른 모듈로 넘기는 부수 효과를 원래 작업과 같은 트랜잭션에서 기록해 두는 테이블이다.
-- 기록만 하는 쪽(각 모듈)과 읽어서 처리하는 쪽(발행기)이 서로 다른 모듈이라 다른 테이블과 FK를 걸지 않는다.
-- aggregate_id가 가리키는 행은 모듈마다 다르고, 이벤트는 가리키는 행이 지워진 뒤에도 처리돼야 하기 때문이다.
--
-- locked_until은 발행기가 이벤트를 처리하는 동안 다른 발행기가 같은 행을 집지 못하게 하는 임대(lease) 만료 시각이다.
-- 발행기는 메일 발송 같은 외부 호출을 트랜잭션 밖에서 하므로 행 잠금(FOR UPDATE)을 처리가 끝날 때까지 쥘 수 없다.
-- 그래서 행을 집는 짧은 트랜잭션에서 locked_until을 미래 시각으로 채워 두고 커밋한다.
-- 다른 발행기는 locked_until이 비었거나 이미 지난 PENDING 행만 집는다.
-- 발행기가 처리 도중 죽어도 임대 시간이 지나면 다른 발행기가 그 행을 다시 집는다.
-- 실패한 이벤트는 같은 컬럼에 다음 재시도 시각을 넣어 바로 다시 집히지 않게 한다.
--
-- 시각 컬럼은 V1과 같이 DATETIME(6) UTC이고, 시각은 앱이 Clock으로 정해서 채운다.
CREATE TABLE outbox_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(50) NOT NULL,
    aggregate_type VARCHAR(30) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(2000) NULL,
    published_at DATETIME(6) NULL,
    locked_until DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 발행기가 주기적으로 status = 'PENDING' 행을 id 순서(기록한 순서)로 읽는다.
-- 이 인덱스가 없으면 PUBLISHED 행이 쌓일수록 매번 테이블 전체를 훑고 정렬해야 한다.
-- (status, id)로 만들면 PENDING 구간만 id 순서대로 읽고 정렬을 건너뛴다.
CREATE INDEX idx_outbox_event_status_id ON outbox_event (status, id);
