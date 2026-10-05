-- 원천 데이터의 기준일을 저장하려고 컬럼을 더한다.
-- 파일로 받는 원천(자연휴양림)은 파일 기준일을 넣고, 기준일이 없는 원천(고캠핑 API)은 NULL로 둔다.
-- synced_at은 동기화가 값을 쓴 시각이고, source_date는 원천이 데이터를 만든 날짜라서 따로 둔다.
ALTER TABLE public_spot_detail
    ADD COLUMN source_date DATE NULL AFTER closed_until;
