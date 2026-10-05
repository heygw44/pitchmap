-- 스스로 밝힌 연령대의 값 중 SIXTIES_PLUS(60대 이상)가 12자라서 VARCHAR(10) 열에 들어가지 않는다.
-- 그래서 다른 상태 값 열과 같은 VARCHAR(20)으로 늘린다. 값을 지울 수 있어야 하므로 NULL 허용은 그대로 둔다.
ALTER TABLE member MODIFY COLUMN self_age_group VARCHAR(20) NULL;
