-- 그루잇(GROOIT) – 메인 서버 DB 스키마 (MySQL 8)
-- 기준: 이나연 님 ERD(ERD Cloud, 11/7) + 메인 서버 API 명세서 v2.2 (11/9 trees.background_color 추가)
-- ERD Cloud 내보내기에 빠진 AUTO_INCREMENT · UNIQUE · 외래 키(ON DELETE CASCADE) · 인덱스를 채웠다.
-- 나무 삭제·회원 탈퇴 때 아래 기록이 모두 함께 지워진다.

CREATE DATABASE IF NOT EXISTS jisik_namu
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE jisik_namu;

-- 회원
CREATE TABLE users (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '회원 ID',
  nickname      VARCHAR(20)  NOT NULL COMMENT '닉네임 (로그인 아이디, 2~10자)',
  password_hash VARCHAR(60)  NOT NULL COMMENT '숫자 비밀번호 BCrypt 해시',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '가입일',
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_nickname (nickname)
) ENGINE=InnoDB COMMENT='회원';

-- 나무 (과목 1개 = 나무 1그루)
CREATE TABLE trees (
  id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '나무 ID',
  user_id      BIGINT      NOT NULL COMMENT '회원 ID',
  subject_name VARCHAR(30) NOT NULL COMMENT '과목 이름 (1~20자)',
  study_level  ENUM('ELEMENTARY','MIDDLE','HIGH','UNIVERSITY','SELF_STUDY') NOT NULL COMMENT '공부 수준',
  shape        ENUM('BASIC','GINKGO','MAPLE') NOT NULL DEFAULT 'BASIC' COMMENT '나무 모양 (MVP는 BASIC)',
  background_color ENUM('PINK','YELLOW','GREEN','BLUE') NOT NULL DEFAULT 'BLUE' COMMENT '나무 배경 색상',
  is_paid     BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '결제로 추가한 나무 (MVP는 false)',
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '만든 날',
  PRIMARY KEY (id),
  KEY idx_trees_user (user_id),
  CONSTRAINT fk_trees_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='나무';

-- 공부 기록 (공부 입력 1번 = 1행)
CREATE TABLE studies (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '공부 기록 ID',
  tree_id        BIGINT       NOT NULL COMMENT '나무 ID',
  subject_name   VARCHAR(30)  NOT NULL COMMENT '입력 때 나무 과목 이름 (복사)',
  study_level    ENUM('ELEMENTARY','MIDDLE','HIGH','UNIVERSITY','SELF_STUDY') NOT NULL COMMENT '입력 때 나무 공부 수준 (복사)',
  input_type     ENUM('IMAGE','TEXT') NOT NULL COMMENT '입력 방식',
  text_content   TEXT         NULL COMMENT '텍스트 입력 내용',
  memo           VARCHAR(200) NULL COMMENT '메모',
  extracted_text TEXT         NULL COMMENT 'AI가 사진에서 읽은 내용',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '공부한 날',
  PRIMARY KEY (id),
  KEY idx_studies_tree (tree_id, created_at),
  CONSTRAINT fk_studies_tree FOREIGN KEY (tree_id) REFERENCES trees (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='공부 기록';

-- 노트 사진 (S3 키만 저장)
CREATE TABLE study_images (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '사진 ID',
  study_id   BIGINT       NOT NULL COMMENT '공부 기록 ID',
  s3_key     VARCHAR(300) NOT NULL COMMENT 'S3 비공개 버킷 키',
  sort_order INT          NOT NULL COMMENT '사진 순서 (최대 5장)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_study_images_order (study_id, sort_order),
  CONSTRAINT fk_study_images_study FOREIGN KEY (study_id) REFERENCES studies (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='노트 사진';

-- 잎 (공부 기록 1개 = 잎 1개, 꽃봉오리 포함)
CREATE TABLE leaves (
  id                  BIGINT   NOT NULL AUTO_INCREMENT COMMENT '잎 ID',
  tree_id             BIGINT   NOT NULL COMMENT '나무 ID',
  study_id            BIGINT   NOT NULL COMMENT '공부 기록 ID',
  position            INT      NOT NULL COMMENT '나무 안 순서 1~24 (가지: 1~5 / 6~12 / 13~19 / 20~24)',
  leaf_type           ENUM('GREEN','LIGHT_GREEN','AUTUMN','BROWN','BUD') NOT NULL COMMENT '현재 등급',
  original_type       ENUM('GREEN','LIGHT_GREEN','AUTUMN','BROWN','BUD') NOT NULL COMMENT '처음 정해진 등급 (재도전 시 재도전 결과)',
  first_correct_count INT      NOT NULL COMMENT '등급을 정한 풀이의 맞힌 개수',
  feedback_summary    TEXT     NULL COMMENT 'AI 총평 (복습 성공 시에만 다시 씀)',
  review_due_at       DATETIME NULL COMMENT '복습 가능 시각 (AUTUMN·BROWN만, 실패 시 +1일)',
  recovered_at        DATETIME NULL COMMENT '복습 성공 시각',
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일',
  PRIMARY KEY (id),
  UNIQUE KEY uk_leaves_study (study_id),
  UNIQUE KEY uk_leaves_tree_position (tree_id, position),
  KEY idx_leaves_review (leaf_type, review_due_at),
  CONSTRAINT fk_leaves_tree  FOREIGN KEY (tree_id)  REFERENCES trees (id)   ON DELETE CASCADE,
  CONSTRAINT fk_leaves_study FOREIGN KEY (study_id) REFERENCES studies (id) ON DELETE CASCADE,
  CONSTRAINT ck_leaves_position CHECK (position BETWEEN 1 AND 24)
) ENGINE=InnoDB COMMENT='잎';

-- 잎 주제 (잘함 / 약함 / 복습으로 이해)
CREATE TABLE leaf_topics (
  id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '잎 주제 ID',
  leaf_id BIGINT       NOT NULL COMMENT '잎 ID',
  topic   VARCHAR(100) NOT NULL COMMENT '주제',
  status  ENUM('STRONG','WEAK','RECOVERED') NOT NULL COMMENT '잘함 / 약함 / 복습으로 이해',
  PRIMARY KEY (id),
  KEY idx_leaf_topics_leaf (leaf_id, status),
  CONSTRAINT fk_leaf_topics_leaf FOREIGN KEY (leaf_id) REFERENCES leaves (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='잎 주제';

-- 문제 세트 (첫 풀이 · 재도전 · 복습)
CREATE TABLE quiz_sets (
  id            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '문제 세트 ID',
  study_id      BIGINT   NOT NULL COMMENT '공부 기록 ID',
  leaf_id       BIGINT   NULL COMMENT '잎 ID (REVIEW만)',
  type          ENUM('INITIAL','RETRY','REVIEW') NOT NULL COMMENT '세트 종류',
  attempt_no    INT      NOT NULL COMMENT '같은 공부·같은 종류 안에서 몇 번째',
  correct_count INT      NULL COMMENT '맞힌 개수 (제출 후, 서술형 제외)',
  graded_count  INT      NOT NULL COMMENT '채점 대상 수 (첫 풀이·재도전 6, 복습 3)',
  status        ENUM('CREATED','SUBMITTED') NOT NULL DEFAULT 'CREATED' COMMENT '제출 여부 (CREATED = 풀던 문제)',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '출제일',
  submitted_at  DATETIME NULL COMMENT '제출일',
  PRIMARY KEY (id),
  UNIQUE KEY uk_quiz_sets_attempt (study_id, type, attempt_no),
  KEY idx_quiz_sets_study_status (study_id, status),
  KEY idx_quiz_sets_leaf_status (leaf_id, status),
  CONSTRAINT fk_quiz_sets_study FOREIGN KEY (study_id) REFERENCES studies (id) ON DELETE CASCADE,
  CONSTRAINT fk_quiz_sets_leaf  FOREIGN KEY (leaf_id)  REFERENCES leaves (id)  ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='문제 세트';

-- 문제
CREATE TABLE questions (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '문제 ID',
  quiz_set_id    BIGINT       NOT NULL COMMENT '문제 세트 ID',
  number         INT          NOT NULL COMMENT '번호 (첫 풀이·재도전 1~7, 복습 1~3)',
  difficulty     ENUM('EASY','NORMAL','HARD','ESSAY') NOT NULL COMMENT '난이도',
  type           ENUM('MULTIPLE_CHOICE','ESSAY') NOT NULL COMMENT '문제 형식',
  topic          VARCHAR(100) NOT NULL COMMENT '문제 주제',
  content        TEXT         NOT NULL COMMENT '문제 본문',
  options        JSON         NULL COMMENT '객관식 보기 4개 (서술형은 NULL)',
  answer_index   INT          NULL COMMENT '객관식 정답 0~3',
  explanation    TEXT         NULL COMMENT '해설',
  model_answer   TEXT         NULL COMMENT '서술형 모범답안',
  grading_points JSON         NULL COMMENT '서술형 채점 포인트',
  PRIMARY KEY (id),
  UNIQUE KEY uk_questions_number (quiz_set_id, number),
  CONSTRAINT fk_questions_quiz_set FOREIGN KEY (quiz_set_id) REFERENCES quiz_sets (id) ON DELETE CASCADE,
  CONSTRAINT ck_questions_answer_index CHECK (answer_index IS NULL OR answer_index BETWEEN 0 AND 3)
) ENGINE=InnoDB COMMENT='문제';

-- 답 (문제 1개 = 답 1개)
CREATE TABLE answers (
  id             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '답 ID',
  question_id    BIGINT   NOT NULL COMMENT '문제 ID',
  selected_index INT      NULL COMMENT '객관식 선택 번호 0~3 (안 풀면 NULL)',
  essay_text     TEXT     NULL COMMENT '서술형 답',
  is_correct     BOOLEAN  NULL COMMENT '정답 여부 (서술형은 NULL)',
  ai_feedback    TEXT     NULL COMMENT '서술형 AI 피드백',
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '제출일',
  PRIMARY KEY (id),
  UNIQUE KEY uk_answers_question (question_id),
  CONSTRAINT fk_answers_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='답';

-- 광고 횟수 (회원별 · 위치별)
CREATE TABLE ad_counters (
  user_id    BIGINT NOT NULL COMMENT '회원 ID',
  ad_type    ENUM('LEAF_PLACEMENT','RECORD_VIEW') NOT NULL COMMENT '광고 위치',
  view_count INT    NOT NULL DEFAULT 0 COMMENT '누적 횟수',
  PRIMARY KEY (user_id, ad_type),
  CONSTRAINT fk_ad_counters_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='광고 횟수';
