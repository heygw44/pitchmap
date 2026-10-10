-- 커뮤니티 글의 조회수. 서버는 작성자가 아닌 회원이나 비회원이 상세를 열 때마다 한 문장 UPDATE로 1씩 올린다.
-- 이미 있는 글은 0부터 센다.
ALTER TABLE community_post ADD COLUMN view_count INT NOT NULL DEFAULT 0;
