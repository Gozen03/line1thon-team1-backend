# 그루잇(GROOIT) – 메인 서버

Spring Boot 4.1.2 · Java 17 · Gradle · MySQL 8.4

## 로컬 실행

1. `.env.example`을 복사해 `.env`를 만들고 값을 채운다 (`.env`는 깃에 올리지 않음).
2. 로컬 DB 실행 (3307 포트, 처음 한 번 `db/schema.sql`로 테이블 생성)
   ```bash
   docker compose -f docker-compose.local.yml up -d
   ```
3. IntelliJ에서 `JisikNamuApplication` 실행 → http://localhost:8080

DB를 처음부터 다시 만들려면: `docker compose -f docker-compose.local.yml down -v` 후 다시 `up -d`.

## 배포

EC2 1대에서 Docker Compose로 nginx와 메인 서버를 실행한다 (`deploy/`).

- `main`에 push하면 GitHub Actions가 테스트 후 이미지를 GHCR에 올린다.
- EC2에서 반영: `docker compose pull main && docker compose up -d main`
- 환경변수는 `deploy/.env.main.example` 참고.
