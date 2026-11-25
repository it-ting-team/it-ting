ssafy인들을 위한 관심사 기반 커뮤니티 플랫폼

## 처음 시작하는 법

새로 합류한 개발자가 첫 PR까지 가는 순서다. 자세한 설정은 아래의 각 절에 있다.

### 1. 사람이 먼저 할 일

비밀값, 로그인, 서명 키처럼 AI 에이전트가 대신할 수 없는 것이다.

1. 저장소를 clone하고 [로컬 개발 환경](#로컬-개발-환경)대로 `.env`를 만들어 `docker compose up -d`로 띄운다.
2. [백엔드의 준비](#준비-1)대로 JDK 25와 Docker Desktop을 설치한다. 테스트가 Testcontainers를 쓰므로 Docker가 켜져 있어야 빌드가 통과한다.
3. [커밋 서명](docs/commit-convention.md#커밋-서명)을 설정하고 GitHub에 Signing Key를 등록한다. 서명 없는 커밋은 `dev`에 병합되지 않는다.
4. `gh auth login`으로 GitHub CLI에 로그인한다. PR은 에이전트가 `gh`로 만든다.
5. [AI 에이전트의 처음 한 번](#처음-한-번-1)대로 Playwright MCP를 승인하고, 뜬 Chrome 창에서 GitHub에 로그인한다. API PR의 스크린샷을 올릴 때 쓴다.
6. Jira에서 내가 맡은 티켓과 그 "시작 조건"을 확인한다. 시작 조건의 티켓이 아직 병합되지 않았으면 기다린다.

### 2. 에이전트에게 줄 프롬프트

Claude Code와 Codex 모두 같은 프롬프트를 쓴다. 규칙과 순서는 `AGENTS.md`와 스킬에 있으므로, 프롬프트에는 지금 어느 단계인지만 말한다. 아래 `KAN-48`은 내 티켓 번호로 바꾼다.

**환경 점검** (처음 한 번)

```text
이 저장소에서 처음 개발을 시작해. AGENTS.md와 README.md를 읽고
내 PC가 개발할 준비가 됐는지 점검해 줘.
JDK 25, Docker, .env, docker compose, 커밋 서명, gh 로그인, Playwright MCP,
./gradlew build 통과 여부를 확인하고 결과를 표로 보여 줘.
설정이나 비밀값은 바꾸지 말고, 내가 할 일만 알려 줘.
```

**구조 파악** (코드는 바꾸지 않는다)

```text
내가 맡은 티켓은 KAN-48이야. 시작하기 전에
- 이 서비스의 패키지 구조와 이미 만들어진 공통 장치
  (ApiResult, BusinessException, 세션 로그인, @AllowDuringOnboarding,
  마이그레이션 이름 규칙, Testcontainers 테스트)
- 내 티켓이 기대는 기존 코드와 테이블
- docs/spec, docs/api, docs/db에서 내 티켓에 해당하는 부분
을 설명해 줘. 코드는 아직 바꾸지 마.
```

**티켓 시작**

```text
KAN-48 시작하자.
```

스킬 `work-plan`이 최신 `origin/dev`에서 브랜치를 만들고, `docs/work/KAN-48.md`에 계획서를 쓰고, "이번에 정할 것"을 묻는다. 에이전트가 Jira에 연결돼 있지 않으면 티켓 설명을 프롬프트 아래에 붙여 넣는다.

**구현** (계획을 읽고 "이번에 정할 것"에 답한 뒤)

```text
진행해.
```

**PR**

```text
PR 만들어 줘.
```

로컬 리뷰(`code-review`) → 빌드 → PR → 호출 결과 스크린샷(`api-evidence`) 순서로 한다.

**리뷰 반영** (PR에 Codex 리뷰가 달린 뒤)

```text
Codex 리뷰 확인하고 P0, P1 있으면 고쳐 줘.
```

고친 뒤 PR에 `@codex review` 댓글로 다시 리뷰를 받는다([코드 리뷰](#코드-리뷰)).

### 3. 지킬 것

- "이번에 정할 것"은 에이전트의 제안을 그대로 승인하지 말고 직접 판단한다. 명세에 없는 규칙이 여기서 정해진다. 다른 티켓과 겹치는 것(예: 모임 대화방, 알림 서비스의 모양)은 그 담당자와 맞춘 뒤 답한다.
- 계획을 승인하기 전에는 코드를 바꾸지 않는다. 에이전트가 먼저 바꾸려 하면 멈춘다.
- 병합은 사람이 한다. Codex 리뷰의 P0, P1이 처리됐는지 확인하고 병합한다.
- 비밀값(`.env`, 웹후크 URL, 키)은 사람이 넣는다. 에이전트에게 붙여 넣지 않는다.

## 로컬 개발 환경

로컬에서 쓰는 MySQL, Redis, Mailpit을 Docker Compose로 띄운다. Mailpit은 서버가 보낸 메일(인증 코드)을 실제로 보내지 않고 받아 둔다. 받은 메일은 http://localhost:8025 에서 본다.

### 준비

- Docker Desktop(Docker Compose v2 포함)

### 처음 한 번

```bash
cp .env.example .env
```

내 PC에 MySQL이나 Redis가 이미 떠 있어 포트가 겹치면 `.env`의 `MYSQL_PORT`, `REDIS_PORT`를 다른 번호(예: `3307`)로 바꾼다.

SSAFY 인증 코드(Mattermost DM)를 실제로 보내 보려면 `.env`의 `MATTERMOST_WEBHOOK_URLS_<기수>`에 그 기수 Mattermost의 Incoming Webhook URL을 넣는다. 비워 두면 그 기수로는 보내지 못하고 `503`이 난다. URL은 비밀값이라 공유하지 않는다.

### 실행과 종료

```bash
docker compose up -d     # 실행
docker compose ps        # 상태 확인 (STATUS가 healthy면 준비됨)
docker compose down      # 종료 (데이터는 남음)
docker compose down -v   # 종료하고 데이터까지 지움
```

### 접속 정보

| 항목 | 값 |
|---|---|
| MySQL | `localhost:${MYSQL_PORT}`, DB `${MYSQL_DATABASE}`, 사용자 `${MYSQL_USER}` |
| Redis | `localhost:${REDIS_PORT}` |

값은 `.env`에 적은 것을 따른다.

## 백엔드

Spring Boot 4.2, Java 25, Gradle, Spring Data JPA, Flyway. 코드는 `backend/`에 있다.

### 준비

- JDK 25. IntelliJ의 Project Structure → SDK → Download JDK에서 받거나 [Temurin](https://adoptium.net/)을 설치한다.
- 터미널에서 실행하면 `JAVA_HOME`이 JDK 25를 가리켜야 한다.
- Docker Desktop. 서버는 로컬 MySQL에, 테스트는 Testcontainers가 띄우는 MySQL에 붙는다.

### 실행

먼저 [로컬 개발 환경](#로컬-개발-환경)대로 `docker compose up -d`로 MySQL을 띄운다. `local` 프로필은 저장소 루트의 `.env`에서 DB 접속 값을 읽는다.

```bash
cd backend
./gradlew bootRun                                   # local 프로필로 실행
./gradlew bootRun --args='--spring.profiles.active=test'   # 다른 프로필로 실행
./gradlew build                                     # 빌드와 테스트
```

| 주소 | 내용 |
|---|---|
| http://localhost:8080/swagger-ui.html | API 문서(Swagger). `prod` 프로필에서는 열리지 않는다 |
| http://localhost:8080/actuator/health | 헬스체크 |

### 브라우저 푸시 키

브라우저 푸시를 실제로 보내 보려면 VAPID 키를 만들어 `.env`에 넣는다. 키를 비워 두면 서버는 정상으로 뜨고 푸시만 보내지 않는다.

```bash
cd backend
./gradlew -q vapidKeys    # ITTING_PUSH_PUBLIC_KEY=..., ITTING_PUSH_PRIVATE_KEY=... 두 줄을 출력한다(파일은 만들지 않는다)
```

출력된 두 줄을 `.env`에 붙여 넣고, 푸시 서비스가 문제를 알릴 연락처 `ITTING_PUSH_SUBJECT=mailto:<팀 이메일 주소>`도 적는다. 개인 키(`ITTING_PUSH_PRIVATE_KEY`)는 비밀값이라 공유하지 않는다.

### Google 로그인 설정

Google 로그인과 설정의 Google 연동을 실제로 써 보려면 Google Cloud 콘솔에서 "웹 애플리케이션" OAuth 클라이언트를 만들어 값을 `.env`에 넣는다. 비워 두면 서버는 정상으로 뜨고 Google 로그인 · 연동만 실패한다.

1. "승인된 리디렉션 URI"에 `.env`의 `ITTING_GOOGLE_REDIRECT_URI`와 같은 주소(로컬은 `http://localhost:8080/api/v1/oauth/google/callback`)를 등록한다.
2. 클라이언트 ID와 보안 비밀을 `ITTING_GOOGLE_CLIENT_ID`, `ITTING_GOOGLE_CLIENT_SECRET`에 적는다. 보안 비밀은 비밀값이라 공유하지 않는다.

### 프로필

| 프로필 | 쓰는 곳 | 설정 파일 |
|---|---|---|
| `local` | 내 PC. 프로필을 고르지 않으면 이것으로 뜬다 | `application-local.yml` |
| `test` | 테스트 서버 | `application-test.yml` |
| `prod` | 운영 서버 | `application-prod.yml` |

모든 프로필에 같은 설정은 `application.yml`에 둔다. `test`, `prod` 프로필의 DB 접속 값은 환경 변수(`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`)로 넣는다.

### DB 마이그레이션

테이블은 Flyway가 `backend/src/main/resources/db/migration/`의 SQL을 번호 순서대로 적용해 만든다. 서버가 뜰 때 아직 적용하지 않은 파일만 적용한다.

- 새 테이블은 그 기능의 PR에서 `V<yyyyMMdd>_<HHmm>__<설명>.sql`로 더한다. 번호는 파일을 만든 시각(한국 시간)이다. 예: `V20261123_1430__create_meetup.sql`. 여러 사람이 동시에 만들어도 번호가 겹치지 않게 하려는 것이다(`AGENTS.md`의 DB 절).
- 이미 dev에 들어간 파일은 고치지 않는다. 바꿀 것이 있으면 새 번호의 파일을 더한다.
- 엔티티와 테이블이 다르면 서버가 뜨지 않는다(`ddl-auto: validate`).

로컬 DB를 처음 상태로 되돌리려면 `docker compose down -v` 후 다시 띄운다. 데이터가 모두 지워진다.

## 배포

| 무엇 | 올라가는 것 | 다시 배포되는 때 | 주소 |
|---|---|---|---|
| 테스트 서버 (GCP) | 프론트엔드 + 백엔드, 실제 데이터 | `dev` → `test` PR을 병합할 때 | 첫 배포 뒤에 적는다 |
| 목 사이트 (Vercel) | 프론트엔드만, 목 데이터 | `dev`에 `frontend/` 변경이 병합될 때 | 첫 배포 뒤에 적는다 |

구성, 처음 만드는 법, 로그 보기, 되돌리기는 [테스트 서버와 목 사이트](docs/infra/test-server.md)에 있다.

## AI 에이전트

Claude Code와 Codex는 같은 규칙과 스킬을 쓴다.

| 파일 | 내용 |
|---|---|
| `AGENTS.md` | 규칙 원본. Codex가 읽는다 |
| `CLAUDE.md` | `AGENTS.md`를 불러온다. Claude Code가 읽는다 |
| `.agents/skills/` | 스킬 원본 (Codex) |
| `.claude/skills/` | 스킬 사본 (Claude Code). 직접 고치지 않는다 |
| `.mcp.json`, `.codex/config.toml` | Playwright MCP 설정 (브라우저 조작, 스크린샷) |

스킬은 일하는 순서대로 `work-plan`(계획서 · 결과) → `code-review` → `pr-create` → `api-evidence`(API PR)다. 언제 쓰는지는 `AGENTS.md`의 "스킬" 절에 있다.

### 처음 한 번

1. Chrome을 설치해 둔다. Playwright MCP가 설치된 Chrome을 쓴다.
2. 쓰는 도구에 맞춰 설정한다.
   - Claude Code: 저장소에서 `claude`를 실행하면 `playwright` MCP를 쓸지 묻는다. 승인한다.
   - Codex: 저장소에서 `codex`를 실행하고 프로젝트를 신뢰(trust)한다. 신뢰하지 않으면 `.codex/config.toml`을 읽지 않는다. 확인: `codex mcp list`에 `playwright`가 보이면 된다.
3. GitHub에 로그인한다. 에이전트가 처음 브라우저를 열면 Chrome 창이 뜬다. 그 창에서 직접 GitHub에 로그인한다.
   - 로그인 정보는 `.playwright-mcp/profile/`에 남는다. git에는 올라가지 않는다.
   - 다음부터는 다시 로그인하지 않아도 된다.

Codex 샌드박스가 네트워크를 막아 `curl`, `gh` 명령이 실패하면, 개인 설정(`~/.codex/config.toml`)에서 workspace-write 샌드박스의 네트워크를 허용한다. 키 이름은 [Codex 설정 문서](https://developers.openai.com/codex/config-reference)를 확인한다.

### 코드 리뷰

| 언제 | 누가 | 기준 |
|---|---|---|
| PR 올리기 전 | 작업한 에이전트(스킬 `code-review`) | `AGENTS.md`의 `Code Review Rules` |
| PR 올린 뒤 | Codex Code Review (자동) | 같은 `Code Review Rules` |
| 병합할 때 | 병합하는 사람 | Codex 리뷰의 P0, P1 지적이 처리됐는지 확인 |

Codex Code Review는 팀원 한 명이 Codex 설정에서 이 저장소의 Code review를 켜서 연결한다. 그 사람의 ChatGPT 플랜 사용량을 쓴다. 한도에 걸려 리뷰가 오지 않을 수 있으므로 병합 필수 조건으로 걸지 않는다. 팀원 승인도 지금은 필수가 아니다. 자동 리뷰는 PR을 열 때와 Draft를 Ready for review로 바꿀 때만 돈다. 푸시할 때마다 돌지 않으므로, 지적을 고친 뒤 다시 받으려면 PR에 `@codex review`라고 댓글을 단다.

리뷰의 언어, 말투, 기준은 `AGENTS.md`의 `Code Review Rules`에서 바꾼다. Codex가 정한 고정 안내문("Codex Review Summary" 등)은 영어로 나올 수 있다.

### 스킬을 고칠 때

원본 `.agents/skills/`를 고친 뒤 사본을 맞춘다. 두 폴더가 다르면 PR의 CI 검사(스킬 동기화)가 실패한다.

```bash
bash scripts/sync-agent-skills.sh
```

## 문서

- [브랜치 전략](docs/branch-strategy.md)
- [커밋 메시지 규칙](docs/commit-convention.md)
- [CI](docs/ci.md)
- [테스트 서버와 목 사이트](docs/infra/test-server.md)
- [서비스 명세](docs/spec/)
- [DB 설계](docs/db/)
- [API 공통 규칙](docs/api-convention.md), [API 명세](docs/api/)
