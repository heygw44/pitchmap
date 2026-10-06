-- 시각 컬럼은 V1과 같이 DATETIME(6) UTC이고, 시각은 앱이 Clock으로 정해서 채운다.
--
-- 고캠핑 동기화가 원천의 운영 상태와 휴장 기간을 저장하고, 원천에서 삭제된 장소를 숨긴 시각을 남기려고 컬럼을 더한다.
-- 서버는 "지금 휴장 중인지"를 조회할 때 오늘 날짜(한국)와 휴장 기간으로 계산한다. 그래서 DB에는 원천 값을 그대로 둔다.
-- 원천에는 운영 상태가 "운영"인데 휴장 기간이 있는 곳도 있어서, 운영 상태 하나로는 지금 휴장인지 알 수 없다.
--
-- operating_status: OPERATING, TEMPORARILY_CLOSED, PERMANENTLY_CLOSED. 값이 없거나 모르는 값이면 NULL이다.
-- source_removed_at: 원천이 삭제로 알려 준 장소를 동기화가 숨긴 시각이다. 이 값이 있어야 동기화가 숨긴 장소와 관리자가 숨긴 장소를
-- 구분해서, 원천에 다시 나타났을 때 동기화가 숨긴 장소만 되돌릴 수 있다.
ALTER TABLE public_spot_detail
    ADD COLUMN operating_status VARCHAR(20) NULL AFTER homepage,
    ADD COLUMN closed_from DATE NULL AFTER operating_status,
    ADD COLUMN closed_until DATE NULL AFTER closed_from,
    ADD COLUMN source_removed_at DATETIME(6) NULL AFTER closed_until;

-- 동기화가 원천에서 받았지만 저장하지 못하고 건너뛴 항목 수(좌표 없음, 기상청 격자 밖 등)다.
-- processed_count는 받은 항목 수라서, 둘을 따로 두면 받은 수와 저장한 수의 차이를 DB만 보고도 알 수 있다.
ALTER TABLE sync_job_run
    ADD COLUMN skipped_count INT NOT NULL DEFAULT 0 AFTER processed_count;
