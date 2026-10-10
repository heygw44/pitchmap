-- 신고 내용(member_report.content)과 제재 사유(sanction.reason)를 NULL로 둘 수 있게 바꾼다.
-- 처리가 끝난 지 1년이 지나면 정리 작업이 이 내용 컬럼을 NULL로 지우고, 신고와 제재 행 자체는 기록으로 남기기 때문이다.
-- 새 신고와 새 제재는 내용과 사유가 반드시 있어야 하므로, 이 검증은 DB가 아니라 엔티티의 생성 메서드가 계속 맡는다.
-- 타입과 문자 집합은 V14에서 만든 그대로 두고 NULL 허용만 바꾼다.
ALTER TABLE member_report MODIFY COLUMN content VARCHAR(1000) NULL;
ALTER TABLE sanction MODIFY COLUMN reason VARCHAR(500) NULL;
