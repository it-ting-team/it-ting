ssafy인들을 위한 관심사 기반 커뮤니티 플랫폼

## 로컬 개발 환경

로컬에서 쓰는 MySQL과 Redis를 Docker Compose로 띄운다.

### 준비

- Docker Desktop(Docker Compose v2 포함)

### 처음 한 번

```bash
cp .env.example .env
```

내 PC에 MySQL이나 Redis가 이미 떠 있어 포트가 겹치면 `.env`의 `MYSQL_PORT`, `REDIS_PORT`를 다른 번호(예: `3307`)로 바꾼다.

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

### 프로필

| 프로필 | 쓰는 곳 | 설정 파일 |
|---|---|---|
| `local` | 내 PC. 프로필을 고르지 않으면 이것으로 뜬다 | `application-local.yml` |
| `test` | 테스트 서버 | `application-test.yml` |
| `prod` | 운영 서버 | `application-prod.yml` |

모든 프로필에 같은 설정은 `application.yml`에 둔다. `test`, `prod` 프로필의 DB 접속 값은 환경 변수(`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`)로 넣는다.

### DB 마이그레이션

테이블은 Flyway가 `backend/src/main/resources/db/migration/`의 SQL을 번호 순서대로 적용해 만든다. 서버가 뜰 때 아직 적용하지 않은 파일만 적용한다.

- 새 테이블은 그 기능의 PR에서 `V<번호>__<설명>.sql`로 더한다. 예: `V2__create_meetup_tables.sql`
- 이미 dev에 들어간 파일은 고치지 않는다. 바꿀 것이 있으면 새 번호의 파일을 더한다.
- 엔티티와 테이블이 다르면 서버가 뜨지 않는다(`ddl-auto: validate`).

로컬 DB를 처음 상태로 되돌리려면 `docker compose down -v` 후 다시 띄운다. 데이터가 모두 지워진다.

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

원본 `.agents/skills/`를 고친 뒤 사본을 맞춘다. 두 폴더가 다르면 PR의 `agent-skills-sync` 검사가 실패한다.

```bash
bash scripts/sync-agent-skills.sh
```

## 문서

- [브랜치 전략](docs/branch-strategy.md)
- [커밋 메시지 규칙](docs/commit-convention.md)
- [서비스 명세](docs/spec/)
- [DB 설계](docs/db/)
- [API 공통 규칙](docs/api-convention.md), [API 명세](docs/api/)
