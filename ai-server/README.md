# 지식 나무 키우기 — AI 서버

메인 서버가 부르는 AI 서버입니다. 문제 출제·피드백·복습 출제·복습 피드백 4가지만 합니다.
DB는 없고, 채점과 잎 등급은 메인 서버가 합니다. 요청·응답 형식은 `06_AI서버_API명세서`를 보세요.

| 기능 | 경로 |
| --- | --- |
| 상태 확인 | `GET /health` |
| 문제 출제 (+ 노트 읽기) | `POST /ai/v1/questions` |
| 피드백 | `POST /ai/v1/feedback` |
| 복습 출제 | `POST /ai/v1/review-questions` |
| 복습 피드백 | `POST /ai/v1/review-feedback` |

`AI_INTERNAL_KEY`가 설정되어 있으면 모든 POST에 같은 값의 `X-Internal-Key` 헤더가 필요합니다. 운영에서는 꼭 설정합니다.

- 모델: OpenAI GPT-6 Luna (`AI_PROVIDER=openai`, `AI_MODEL=gpt-6-luna`). Gemini는 예비 (`AI_PROVIDER=gemini`).
- 배포: 메인 서버와 같은 EC2의 Docker Compose 안에서 `http://ai:8000`. 외부에 공개하지 않음.
- 이미지: `main`에 머지되면 GitHub Actions가 `ghcr.io/gozen03/ai-server:latest`로 올림.

## 폴더 구조

```
ai-server/
├─ config/            Django 설정 (DB 없음), 주소 연결
├─ ai/
│  ├─ views.py        API 입구: 내부 키 확인, 요청 형식 검사, { success, data, error } 감싸기
│  ├─ schemas.py      요청 형식과 AI 출력 형식 (Pydantic)
│  ├─ services.py     처리 순서: 시간 예산 45초 → AI 호출 → 검사 → 필요하면 1회 재요청 → 보기 섞기
│  ├─ prompts.py      AI에게 보내는 지시문 (문제 품질은 여기를 고치면 됨)
│  ├─ llm.py          AI 모델 호출 (OpenAI 기본, Gemini 예비)
│  ├─ validation.py   AI 결과 검사, 보기 섞기
│  ├─ images.py       사진 내려받기, HEIC → JPEG, 긴 변 1568px로 줄이기
│  ├─ mock.py         AI_MOCK=true일 때 쓰는 예시 결과
│  └─ tests.py        테스트 (AI 키 없이 실행)
├─ scripts/measure.py 실제 모델 응답 시간·비용 측정
├─ Dockerfile
└─ requirements.txt
```

## 로컬에서 실행 (Windows Git Bash 기준)

```bash
cd ai-server
python -m venv .venv
source .venv/Scripts/activate        # macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
```

**AI 키 없이 (Mock)**

```bash
AI_MOCK=true DJANGO_DEBUG=true python manage.py runserver 8000
curl http://localhost:8000/health
```

**실제 모델로**

```bash
export OPENAI_API_KEY="sk-proj-..."     # = 양옆에 빈칸 없이
DJANGO_DEBUG=true python manage.py runserver 8000
```

PowerShell은 `export` 대신 `$env:OPENAI_API_KEY="sk-proj-..."` 형식으로 넣습니다.
키는 코드나 GitHub에 넣지 않습니다.

## 테스트

```bash
python manage.py test ai
```

AI 키 없이 돌아갑니다. GitHub Actions도 PR마다 같은 테스트를 실행합니다.

## 응답 시간·비용 재기

```bash
export OPENAI_API_KEY="sk-proj-..."
python scripts/measure.py --images ../../photos/1.jpg ../../photos/2.heic ../../photos/3.jpg ../../photos/4.jpg ../../photos/5.jpg --runs 3
```

사진 출제, 텍스트 5,000자 출제, 피드백, 복습 출제, 복습 피드백을 `--runs`번씩 재고 맨 아래에 팀 공유용 요약을 출력합니다.

## Docker

```bash
docker build -t ai-server .
docker run --rm -p 8000:8000 --env-file .env ai-server
curl http://localhost:8000/health      # 200이면 성공
```

컨테이너는 gunicorn `0.0.0.0:8000`, `--workers 2 --threads 4 --timeout 60`으로 실행됩니다.

### docker-compose (EC2)

```yaml
ai:
  image: ghcr.io/gozen03/ai-server:latest
  env_file: .env.ai
  expose: ["8000"]
  restart: always
```

## 환경변수 (`.env.ai`)

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `OPENAI_API_KEY` | O | OpenAI 플랫폼의 API 키 (`sk-proj-...`) |
| `AI_INTERNAL_KEY` | O (운영) | 메인 서버와 같은 값. 비우면 키 검사 안 함 |
| `DJANGO_SECRET_KEY` | O (운영) | 아무 긴 문자열 |
| `AI_PROVIDER` | X | 기본 `openai`. 예비로 `gemini` |
| `AI_MODEL` | X | 기본 `gpt-6-luna` (gemini면 `gemini-2.5-flash`) |
| `AI_REASONING_EFFORT` | X | 기본 `none`(빠름). 품질이 아쉬우면 `low` |
| `AI_MOCK` | X | `true`면 AI 없이 예시 결과 |
| `GEMINI_API_KEY`, `AI_THINKING_BUDGET` | X | Gemini로 바꿀 때만 |
| `DJANGO_DEBUG` | X | 로컬 개발만 `true` |
| `DJANGO_ALLOWED_HOSTS` | X | `ai`, `localhost` 외에 더 허용할 주소 (쉼표로) |

## 처리 규칙 메모

- 공부 내용이 100자 이상인데 모델이 "내용 부족"(`enoughContent=false`)을 주면 믿지 않고 1회 재요청합니다. 그래도 안 되면 502 `AI_GENERATION_FAILED`(다시 시도)입니다. 100자 미만이면 422 `CONTENT_TOO_SHORT`입니다.
- 사진 입력이면 `extractedText`를 항상 채웁니다. 비어 있으면 1회 재요청합니다.
- 검증 실패 시 재요청은 시간 예산(45초)이 남아 있을 때 1회만 합니다.

## Mock에서 에러 일부러 내기

`AI_MOCK=true`일 때 `content`나 `memo`에 `MOCK_ERROR:코드`를 넣으면 그 에러가 납니다.
`NOTE_UNREADABLE`, `CONTENT_TOO_SHORT`, `AI_GENERATION_FAILED`, `AI_TIMEOUT`, `IMAGE_FETCH_FAILED`

예: `"memo": "MOCK_ERROR:NOTE_UNREADABLE"` → 422 응답
