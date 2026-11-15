# 그루잇(GROOIT) – 백엔드

> 공부한 노트를 찍어 올리면 AI가 그 내용으로 문제를 내고, 푼 결과가 과목 나무의 잎으로 자라는 서비스

멋쟁이사자처럼 1호선톤 1팀 · 메인 서버(Spring Boot)와 AI 서버(Django)

| | 주소 |
| --- | --- |
| 서비스 | https://line1thon-grooit.vercel.app |
| API | `https://api.30nezog.shop/api/v1` |
| API 문서 (Swagger) | https://api.30nezog.shop/swagger-ui/index.html |

## 서비스 흐름

1. **나무 심기** – 과목마다 나무를 하나 심는다 (무료 3그루).
2. **공부 입력** – 노트 사진(최대 5장) 또는 글을 올리면 AI가 노트를 읽고 7문제를 낸다. 객관식 6개(쉬움·보통·어려움 2개씩)와 서술형 1개다.
3. **채점과 잎** – 객관식 맞힌 개수로 잎 등급이 정해지고, AI가 총평과 서술형 피드백을 쓴다. 잎은 나무에 붙는다.

   | 맞힌 개수 (6문제) | 6 | 5~4 | 3~2 | 1 | 0 |
   | --- | --- | --- | --- | --- | --- |
   | 잎 | 싱싱한 잎 | 연두잎 | 단풍잎 | 마른 잎 | 잎눈 |

4. **복습** – 단풍잎·마른 잎은 3일 뒤 약한 주제로 복습 3문제가 나온다. 다 맞히면 싱싱한 잎이 되고, 틀리면 1일 뒤 다시 복습한다.
5. **재도전** – 잎눈(0개)은 약한 주제를 반영한 새 7문제로 바로 다시 도전할 수 있다.

## 구조

```
 [프론트 (Vercel)]
        │ HTTPS
        ▼
 ┌─────────── AWS EC2 · Docker Compose ───────────┐
 │  nginx ──▶ 메인 서버 ──(내부망)──▶ AI 서버    │──▶ OpenAI
 │            (Spring Boot)          (Django)     │
 └────────────────┬───────────────────────────────┘
                  ├──▶ RDS (MySQL 8.4)
                  └──▶ S3 (노트 사진)
```

| 폴더 | 서버 | 담당 | 하는 일 |
| --- | --- | --- | --- |
| 맨 위 (`src/` 등) | 메인 서버 | 고재민 | 회원·나무·공부·채점·잎·복습·광고 API, DB, 사진 저장 |
| [`ai-server/`](ai-server) | AI 서버 | 이나연 | 노트 읽기, 문제 출제, 피드백, 복습 출제 |

- 프론트는 메인 서버만 부르고, AI 서버는 외부에 공개하지 않는다. 메인 서버가 내부 키를 붙여 같은 EC2 안에서 부른다.
- **객관식 채점은 메인 서버가 한다.** AI가 문제와 함께 정답 번호를 주면 서버가 저장해 두고 채점한다. 같은 답이면 항상 같은 결과가 나오고, 문제를 보여 주는 응답에는 정답·해설을 넣지 않는다. AI는 정답이 하나로 정해지지 않는 서술형 피드백과 총평을 맡는다.
- 노트 사진은 비공개 S3 버킷에 저장하고, 잠깐만 열리는 Presigned URL로만 보여 준다.

## 메인 서버

**기술 스택** – Java 17, Spring Boot 4.1.2 (Web MVC, Data JPA, Validation, Security), MySQL 8.4, JWT(jjwt), BCrypt, AWS SDK(S3), springdoc-openapi

**API 20개** (`/api/v1`, 자세한 내용은 Swagger)

| 구분 | API |
| --- | --- |
| 회원 | 가입, 로그인, 내 정보, 닉네임 변경, 로그아웃, 탈퇴 |
| 나무 | 숲(나무 목록), 나무 심기, 나무 상세, 나무 수정, 나무 삭제 |
| 공부 | 공부 입력·출제, 답 제출·채점, 잎눈 재도전, 문제 다시 받기 |
| 잎·복습 | 잎 상세, 복습 목록(전체·나무별), 복습 출제 |
| 광고 | 광고 노출 횟수 기록 |

- 응답 형식은 `{ success, data, error }` 하나로 통일하고, 에러는 정해진 `error.code`로 준다.
- AI를 기다리는 동안(10~25초) DB 연결을 잡지 않도록 "확인 → AI 호출 → 저장"으로 트랜잭션을 나눴다.
- 같은 요청이 동시에 여러 번 오면(버튼 연타) 하나만 처리한다. 나무 3그루 제한, 광고 횟수도 동시 요청에 맞게 잠근다.
- 테스트 191개 (실제 MySQL로 API·동시 요청까지 확인).

**패키지 구조**

```
src/main/java/com/line1thon/jisiknamu
├─ domain/        기능별: user, tree, study, quiz, leaf, ad
│  └─ */          controller · service · repository · entity · dto
└─ global/        ai(AI 서버 호출), security(JWT), storage(S3), error, response, config
```

## 로컬 실행

1. `.env.example`을 복사해 `.env`를 만들고 값을 채운다 (`.env`는 깃에 올리지 않음).
2. 로컬 DB 실행 (3307 포트, 처음 한 번 `db/schema.sql`로 테이블 생성)
   ```bash
   docker compose -f docker-compose.local.yml up -d
   ```
3. 서버 실행 → http://localhost:8080/swagger-ui/index.html
   ```bash
   ./gradlew bootRun
   ```
   `.env`에서 `AI_MOCK=true`면 AI 서버 없이 예시 문제로 동작한다.

테스트: `./gradlew test` (로컬 DB가 켜져 있어야 함)

DB를 처음부터 다시 만들려면: `docker compose -f docker-compose.local.yml down -v` 후 다시 `up -d`.

AI 서버 실행 방법은 [`ai-server/README.md`](ai-server/README.md)에 있다.

## 배포

EC2 1대에서 Docker Compose로 nginx(HTTPS), 메인 서버, AI 서버를 실행한다 (`deploy/`). DB는 RDS, 사진은 S3.

- `main`에 push하면 GitHub Actions가 테스트 후 이미지를 GHCR에 올린다. 메인 서버와 AI 서버는 바뀐 폴더에 따라 따로 빌드된다.
- EC2에서 반영: `docker compose pull main && docker compose up -d main` (AI 서버는 `ai`)
- 환경변수는 `deploy/.env.main.example`, `deploy/.env.ai.example` 참고. 실제 값은 EC2에만 둔다.
- DB 구조는 `db/schema.sql`, 운영 중 바꾼 내용은 `db/migration/`.
