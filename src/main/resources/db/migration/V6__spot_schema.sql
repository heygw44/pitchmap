-- 시각 컬럼은 V1과 같이 DATETIME(6) UTC이고, 시각은 앱이 Clock으로 정해서 채운다.
--
-- 좌표는 GPS 위경도 좌표계(SRID 4326)로 저장한다. 이 좌표계의 WKT는 위도, 경도 순서다.
-- MySQL은 공간 컬럼이 NOT NULL이고 SRID가 지정돼 있을 때만 공간 인덱스를 쓴다.
-- 그래서 공간 컬럼은 모두 NOT NULL과 SRID 4326으로 선언한다.
-- SRID를 지정한 컬럼에 다른 SRID 값을 넣으면 MySQL이 거부한다.
--
-- 장소와 회원은 지우지 않고 상태값으로 숨기거나 익명화한다.
-- 그래서 FK는 모두 기본 동작(RESTRICT)으로 두고, 참조되는 행이 지워지지 않게 막는다.

-- 공원·보호지역 경계. 장소를 저장할 때 서버가 이 경계 안에 있는지 판정해서 경고를 붙인다.
-- 경계 데이터를 새 기준일 데이터로 다시 적재할 수 있어서 updated_at을 둔다.
CREATE TABLE protected_area (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    area_type VARCHAR(20) NOT NULL,
    source VARCHAR(10) NOT NULL,
    source_date DATE NOT NULL,
    boundary MULTIPOLYGON NOT NULL SRID 4326,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    SPATIAL INDEX spx_protected_area_boundary (boundary)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 지도에 표시하는 모든 장소(야영장, 자연휴양림, 사용자가 제보한 박지).
-- 기상청 예보 API는 위경도가 아니라 5km 격자 좌표를 받으므로, 서버가 저장할 때 격자 좌표를 계산해 둔다.
-- protected_area_id는 장소가 공원 경계 안일 가능성이 있을 때 해당 경계를 가리킨다.
CREATE TABLE spot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    location POINT NOT NULL SRID 4326,
    address VARCHAR(255) NULL,
    weather_nx SMALLINT NOT NULL,
    weather_ny SMALLINT NOT NULL,
    park_warning BOOLEAN NOT NULL DEFAULT FALSE,
    protected_area_id BIGINT NULL,
    area_checked_at DATETIME(6) NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    SPATIAL INDEX spx_spot_location (location),
    CONSTRAINT fk_spot_protected_area FOREIGN KEY (protected_area_id) REFERENCES protected_area (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 고캠핑 야영장과 자연휴양림의 상세. 장소와 1:1이라 spot_id를 기본 키로 쓴다.
-- 동기화 작업을 다시 돌려도 같은 원천 행이 두 번 들어가지 않도록 (source, external_id)를 UNIQUE로 둔다.
CREATE TABLE public_spot_detail (
    spot_id BIGINT NOT NULL,
    source VARCHAR(20) NOT NULL,
    external_id VARCHAR(50) NOT NULL,
    category VARCHAR(50) NULL,
    facilities JSON NULL,
    phone VARCHAR(30) NULL,
    homepage VARCHAR(255) NULL,
    synced_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (spot_id),
    CONSTRAINT uk_public_spot_detail_source_external_id UNIQUE (source, external_id),
    CONSTRAINT fk_public_spot_detail_spot FOREIGN KEY (spot_id) REFERENCES spot (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 사용자가 제보한 박지의 상세. 장소와 1:1이라 spot_id를 기본 키로 쓴다.
CREATE TABLE bakji_detail (
    spot_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    description VARCHAR(2000) NULL,
    has_water BOOLEAN NOT NULL,
    has_toilet BOOLEAN NOT NULL,
    signal_level VARCHAR(10) NULL,
    ground_type VARCHAR(20) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (spot_id),
    CONSTRAINT fk_bakji_detail_spot FOREIGN KEY (spot_id) REFERENCES spot (id),
    CONSTRAINT fk_bakji_detail_member FOREIGN KEY (reporter_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 회원이 박지에 다녀와서 정보가 맞다고 남기는 확인.
-- 한 회원은 같은 박지를 한 번만 확인할 수 있어서 (spot_id, member_id)를 기본 키로 쓴다.
-- 서버는 확인 수를 따로 저장하지 않고 이 행을 센다. 추가만 하는 테이블이라 updated_at이 없다.
CREATE TABLE bakji_confirmation (
    spot_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (spot_id, member_id),
    CONSTRAINT fk_bakji_confirmation_spot FOREIGN KEY (spot_id) REFERENCES spot (id),
    CONSTRAINT fk_bakji_confirmation_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 박지 신고. 한 회원은 같은 박지를 한 번만 신고할 수 있어서 (spot_id, reporter_id)를 UNIQUE로 둔다.
-- 서버는 신고 수를 따로 저장하지 않고 이 행을 센다. 추가만 하는 테이블이라 updated_at이 없다.
CREATE TABLE bakji_report (
    id BIGINT NOT NULL AUTO_INCREMENT,
    spot_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    reason VARCHAR(20) NOT NULL,
    content VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_bakji_report_spot_id_reporter_id UNIQUE (spot_id, reporter_id),
    CONSTRAINT fk_bakji_report_spot FOREIGN KEY (spot_id) REFERENCES spot (id),
    CONSTRAINT fk_bakji_report_member FOREIGN KEY (reporter_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 공공데이터 적재·동기화 실행 기록. 작업이 실패하면 다음 실행이 progress_cursor부터 이어서 처리한다.
-- 실행하는 동안 진행 위치와 처리 건수를 갱신하므로 updated_at을 둔다.
CREATE TABLE sync_job_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    progress_cursor VARCHAR(100) NULL,
    processed_count INT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    error_message VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
