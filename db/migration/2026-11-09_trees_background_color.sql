-- 이미 테이블이 있는 DB(로컬, RDS)에 나무 배경 색상 컬럼을 추가한다. 새로 만드는 DB는 schema.sql에 들어 있다.
-- 새 서버 이미지를 올리기 전에 실행한다 (서버가 시작할 때 컬럼이 있는지 검사한다).
USE jisik_namu;

ALTER TABLE trees
  ADD COLUMN background_color ENUM('PINK','YELLOW','GREEN','BLUE') NOT NULL DEFAULT 'BLUE' COMMENT '나무 배경 색상'
  AFTER shape;
