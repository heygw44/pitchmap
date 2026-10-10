-- 커뮤니티 글을 분류 없이 한 게시판에 올리기로 해서 분류 컬럼을 지운다.
-- 운영에 이미 있는 글의 분류 값은 더 쓰지 않으므로 컬럼과 함께 지운다.
ALTER TABLE community_post DROP COLUMN category;
