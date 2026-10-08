-- 관리자가 박지를 복구하면 그 박지의 기존 신고를 검토를 마친 것으로 표시한다. reviewed_at은 표시한 시각이고, NULL이면 아직 검토 전이다.
-- 신고 행은 지우지 않는다. 그래서 신고 이력이 남고, 이미 신고한 회원은 (spot_id, reporter_id) 유니크 제약 때문에 같은 박지를 다시 신고할 수 없다.
-- 검토 대기로 바꾸는 5건 판정과 관리자 목록의 신고 수는 reviewed_at이 NULL인 신고만 센다.
-- 조회는 spot_id로 시작하는 기존 유니크 인덱스를 쓰므로 인덱스를 따로 추가하지 않는다.
ALTER TABLE bakji_report ADD COLUMN reviewed_at DATETIME(6) NULL;
