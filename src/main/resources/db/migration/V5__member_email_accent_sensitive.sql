-- 이메일은 대소문자만 구분하지 않고 비교한다.
-- 기존 정렬 규칙 utf8mb4_0900_ai_ci는 악센트도 무시해서 tomás@x.com과 tomas@x.com을 같은 이메일로 본다.
-- as_ci는 악센트를 구분하고 대소문자만 무시한다. 유니크 인덱스는 컬럼을 바꿀 때 새 정렬 규칙으로 다시 만들어진다.
ALTER TABLE member MODIFY email VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci NULL;
