# 지식 나무 키우기 — AI 서버

메인 서버가 부르는 AI 서버입니다. 문제 출제·피드백·복습 출제·복습 피드백 4가지만 합니다.
DB는 없고, 채점은 메인 서버가 합니다. 자세한 형식은 `04_AI서버_API명세서.md`를 보세요.

| 기능 | 경로 |
| --- | --- |
| 상태 확인 | `GET /health` |
| 문제 출제 (+ 노트 읽기) | `POST /ai/v1/questions` |
| 피드백 | `POST /ai/v1/feedback` |
| 복습 출제 | `POST /ai/v1/review-questions` |
| 복습 피드백 | `POST /ai/v1/review-feedback` |

모든 POST는 `X-Internal-Key` 헤더가 필요합니다.

## 폴더 구조

```
config/          Django 설정 (DB 없음), 주소 연결
ai/views.py      API 입구: 내부 키 확인, 요청 형식 검사, 응답 감싸기
ai/schemas.py    요청 형식과 AI 출력 형식 (Pydantic)
ai/services.py   처리 순서: 시간 예산 45초 → AI 호출 → 검사 → 필요하면 1회 재요청 → 보기 섞기
ai/prompts.py    AI에게 보내는 지시문 (문제 품질은 여기를 고치면 됨)
ai/llm.py        AI 모델 호출: OpenAI GPT-6 Luna(기본), Gemini(예비)
ai/validation.py AI 결과 검사, 보기 섞기
ai/images.py     사진 내려받기, HEIC → JPEG, 크기 줄이기
ai/mock.py       AI_MOCK=true일 때 쓰는 예시 결과
ai/tests.py      테스트
```

## 로컬에서 실행

```bash
python -m venv .venv
source .venv/bin/activate          # 윈도우: .venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env               # 값 채우기
```

**AI 키 없이 (Mock):**

```bash
AI_MOCK=true DJANGO_DEBUG=true python manage.py runserver 8000
curl http://localhost:8000/health
```

**GPT-6 Luna로:**

```bash
export OPENAI_API_KEY=발급받은키 AI_INTERNAL_KEY=아무값 DJANGO_DEBUG=true
python manage.py runserver 8000

curl -X POST http://localhost:8000/ai/v1/questions \
  -H "Content-Type: application/json" -H "X-Internal-Key: 아무값" \
  -d '{"quizSetType":"INITIAL","subjectName":"수학","studyLevel":"HIGH","inputType":"TEXT",
       "content":"이차함수 y=ax²+bx+c의 그래프는 포물선이다. 꼭짓점은 ... (20자 이상)"}'
```

윈도우 PowerShell은 `export` 대신 `$env:OPENAI_API_KEY="..."` 형식으로 넣습니다.

## 테스트

```bash
python manage.py test ai
```

AI 키 없이 돌아갑니다. GitHub에 올리면 GitHub Actions가 자동으로 실행합니다.

## 응답 시간·비용 재기 (실제 모델)

```bash
export OPENAI_API_KEY=발급받은키
python scripts/measure.py --images 노트1.jpg 노트2.jpg 노트3.jpg 노트4.jpg 노트5.jpg --runs 3
```

사진 5장 출제, 텍스트 5,000자 출제, 피드백, 복습 출제를 차례로 재고 맨 아래에 팀 공유용 요약을 출력합니다.
한 번 돌리는 데 약 10회 호출, GPT-6 Luna 기준 100원 안쪽입니다. 다른 모델은 `AI_MODEL`과 `--price-in`, `--price-out`(100만 토큰당 달러)을 바꿔서 잽니다.

## Docker

```bash
docker build -t jisik-namu-ai .
docker run --rm -p 8000:8000 --env-file .env jisik-namu-ai
```

컨테이너는 `0.0.0.0:8000`, gunicorn `--timeout 60`으로 실행됩니다.

### 메인 서버 docker-compose에 넣을 예시 (재민 님 참고)

```yaml
services:
  ai:
    image: ghcr.io/<깃허브 계정>/jisik-namu-ai:latest
    env_file: ./ai.env          # AI_INTERNAL_KEY, OPENAI_API_KEY, AI_MODEL, DJANGO_SECRET_KEY
    expose:
      - "8000"                  # 외부 공개 안 함. 메인 서버는 http://ai:8000 으로 호출
    restart: unless-stopped
```

## 환경변수

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `AI_INTERNAL_KEY` | O | 메인 서버와 같은 내부 키 |
| `OPENAI_API_KEY` | O (Mock 아닐 때) | OpenAI 플랫폼에서 받은 키 |
| `AI_PROVIDER` | X | 기본 `openai`. 예비로 `gemini` |
| `AI_MODEL` | X | 기본 `gpt-6-luna` (gemini면 `gemini-2.5-flash`) |
| `AI_REASONING_EFFORT` | X | 기본 `none`(추론 끔, 빠름). 품질이 아쉬우면 `low` |
| `AI_MOCK` | X | `true`면 AI 없이 예시 결과 |
| `GEMINI_API_KEY`, `AI_THINKING_BUDGET` | X | Gemini로 바꿀 때만 |
| `DJANGO_SECRET_KEY` | O (운영) | 아무 긴 문자열 |
| `DJANGO_DEBUG` | X | 로컬 개발만 `true` |
| `DJANGO_ALLOWED_HOSTS` | X | `ai`, `localhost` 외에 더 허용할 주소 (쉼표로) |

## Mock에서 에러 일부러 내기

`AI_MOCK=true`일 때 `content`나 `memo`에 `MOCK_ERROR:코드`를 넣으면 그 에러가 납니다.
메인 서버 에러 처리 테스트용입니다.

`NOTE_UNREADABLE`, `CONTENT_TOO_SHORT`, `AI_GENERATION_FAILED`, `AI_TIMEOUT`, `IMAGE_FETCH_FAILED`

예: `"memo": "MOCK_ERROR:NOTE_UNREADABLE"` → 422 응답
