-- 이용 정지를 받은 회원의 결제를 마친(CONFIRMED) 신청에 서버가 기록하는 시각이다.
-- 결제 대기 신청은 정지가 확정될 때 서버가 취소하지만, 결제를 마친 신청은 환불 여부를 관리자가 정해야 해서 그대로 두고 이 시각만 적는다.
-- 정지가 끝나거나 풀려도 지우지 않는다. 관리자가 처리 여부를 알아볼 수 있어야 하기 때문이다.
ALTER TABLE program_application ADD COLUMN review_requested_at DATETIME(6) NULL;
