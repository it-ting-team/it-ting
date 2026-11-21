# 에이전트 작업 규칙

Claude Code와 Codex가 이 저장소에서 일할 때 따르는 규칙이다. Codex는 이 파일을 바로 읽고, Claude Code는 `CLAUDE.md`를 거쳐 읽는다.

## 저장소 지도

| 경로 | 내용 |
|---|---|
| `backend/` | Spring Boot 4.2, Java 25, Gradle, Spring Data JPA. 실행 방법은 `README.md`의 백엔드 절 |
| `backend/src/main/resources/db/migration/` | Flyway 마이그레이션. DB 테이블은 여기서만 만든다 |
| `frontend/` | Next.js (KAN-25에서 추가) |
| `docs/spec/` | 서비스 명세. 무엇을 만드는가 |
| `docs/db/` | DB 설계. 무엇을 저장하는가 |
| `docs/api/` | API 명세. 어떻게 주고받는가. 공통 규칙은 `docs/api-convention.md` |
| `docs/branch-strategy.md` | 브랜치 이름, 병합 흐름, 보호 규칙 |
| `docs/commit-convention.md` | 커밋 메시지, 작성자, 서명 |
| `compose.yaml` | 로컬 MySQL, Redis |
| `.agents/skills/` | 스킬 원본 |
| `.claude/skills/` | 스킬 사본 (Claude Code용) |

## 작업 전에 읽을 문서

| 하려는 일 | 먼저 읽을 문서 |
|---|---|
| 기능 구현 | `docs/spec/<번호>-*.md`, `docs/api/<번호>-*.md`, `docs/db/<영역>.md` |
| API 추가 · 변경 | 위 문서 + `docs/api-convention.md` |
| 브랜치 만들기 | `docs/branch-strategy.md` |
| 커밋 | `docs/commit-convention.md` |
| PR 만들기 | 스킬 `pr-create` |

명세(`docs/spec/`)와 API 명세(`docs/api/`)의 번호는 같은 화면을 가리킨다. 예: `docs/spec/07-meetup.md` ↔ `docs/api/07-meetup.md` ↔ `docs/db/meetup.md`.

## 반드시 지킬 것

- MUST: 새 작업 브랜치는 최신 `origin/dev`에서 만든다.
- MUST: 푸시와 PR 전에 `git fetch origin` 후 `git merge-tree --write-tree HEAD origin/dev`로 충돌이 없는지 확인한다.
- MUST: 명세와 다르게 구현해야 하면 코드를 바꾸기 전에 사용자에게 묻는다.
- MUST: 구현하면서 API의 주소, 요청, 응답, 에러 코드가 `docs/api/`와 달라지면 같은 PR에서 `docs/api/`도 고친다.
- MUST: 커밋, 푸시, PR 생성, 병합은 사용자가 요청한 경우에만 한다.

### 백엔드 코드

패키지는 영역별로 나누고, 영역 안을 계층으로 나눈다. 영역 이름은 `docs/api/`의 영역을 따른다(`campus`, `meetup`, `chat` 등).

```text
com.itting
├─ global/        여러 영역이 함께 쓰는 것(응답 틀, 예외, 설정, 부모 엔티티)
└─ <영역>/
   ├─ controller/  <대상>Controller
   ├─ service/     <대상>Service
   ├─ repository/  <대상>Repository
   ├─ domain/      엔티티, enum
   └─ dto/         <무엇>Request, <무엇>Response
```

- MUST: 요청 · 응답 DTO는 `record`로 만들고 이름은 `<무엇>Request`, `<무엇>Response`로 끝낸다. 엔티티를 응답으로 그대로 내보내지 않는다.
- MUST: 컨트롤러는 `ApiResult<T>`를 돌려준다. 성공은 `ApiResult.ok(data)`, 돌려줄 값이 없으면 `ApiResult.ok()`다.
- MUST: 실패는 `BusinessException`을 던져 알린다. 영역의 에러 코드는 `ErrorCode`를 구현한 `<영역>ErrorCode` enum에 두고, 상수 이름이 응답의 `code`다. 공통 코드는 `CommonErrorCode`에 있다.
- MUST NOT: 컨트롤러나 서비스에서 실패 응답 본문을 직접 만들지 않는다. 응답 틀로 바꾸는 일은 `GlobalExceptionHandler`가 한다.
- 입력 검증은 DTO와 메서드 인자에 Bean Validation(`@NotBlank`, `@Size`, `@Max` 등)으로 붙인다. 실패하면 `400 INVALID_INPUT`과 `fields`가 자동으로 나간다.

### DB

- MUST: 테이블은 그 기능을 만드는 PR에서 Flyway 마이그레이션 `V<번호>__<설명>.sql`로 만든다. 번호는 dev에 있는 가장 큰 번호의 다음이다.
- MUST: 테이블을 만들거나 바꾸면 `docs/db/<영역>.md`와 맞춘다. 문서에 "미정"인 칸은 그 PR에서 정하고 문서에도 적는다.
- MUST: 시각은 `java.time.Instant`로 다루고 칼럼은 `DATETIME(6)`으로 만든다. DB에는 UTC로 저장된다.
- MUST: `created_at`, `updated_at`이 있는 엔티티는 `BaseTimeEntity`를, `created_at`만 있는 엔티티는 `BaseCreatedTimeEntity`를 상속한다. 어느 테이블에 무엇을 두는지는 `docs/db/README.md`의 공통 날짜 칼럼을 따른다.
- MUST NOT: dev에 병합된 마이그레이션 파일을 고치지 않는다. 고칠 것이 있으면 새 번호의 마이그레이션을 더한다.
- MUST NOT: `ddl-auto`를 `create`, `update`로 바꾸지 않는다. 모든 프로필에서 `validate`다.
- 테스트에만 필요한 테이블은 `backend/src/test/resources/db/migration/`에 1000번부터 둔다.
- MUST NOT: 비밀값(비밀번호, API 키, 토큰, 웹후크 URL)을 커밋하거나 파일에 적지 않는다. `.env`는 사용자가 채운다.
- MUST NOT: `main`, `dev`에 직접 푸시하거나 강제 푸시하지 않는다. 작업 브랜치의 강제 푸시도 사용자가 요청한 경우에만 한다.
- MUST NOT: 사용자의 git 설정(`user.name`, `user.email`, 서명)을 바꾸지 않고, `--no-gpg-sign`, `--no-verify`를 쓰지 않는다.
- MUST NOT: 비밀번호를 입력하지 않는다. GitHub 로그인처럼 비밀번호가 필요한 단계는 사람에게 넘긴다.
- MUST NOT: 사용자가 띄운 프로세스(서버, 컨테이너)를 끄지 않는다. 포트가 겹치면 다른 포트로 따로 띄운다.

## 완료 기준

### API를 추가하거나 바꾼 작업

아래 세 가지가 모두 있어야 끝난 것이다.

1. 통합 테스트: API마다 정상 경우와 실패 경우(검증 오류, 권한 없음, 없는 대상 가운데 해당하는 것)를 하나 이상 테스트한다. `backend/`에서 `./gradlew build`가 통과한다.
2. Swagger 호출 화면 스크린샷: Swagger UI의 Try it out으로 호출한 화면. 요청과 응답이 한 화면에 보여야 한다.
3. 응답 텍스트: 같은 요청의 상태 코드와 응답 본문.

2와 3은 PR 본문의 `## 호출 결과` 절에 넣는다. 절차는 스킬 `api-evidence`를 따른다.

### 코드를 바꾼 모든 작업

- MUST: PR을 올리기 전에 스킬 `code-review`로 로컬 리뷰를 하고, P0과 P1 지적을 모두 고친다.
- MUST: `backend/`를 바꿨으면 `./gradlew build`가 통과한다. 테스트는 Testcontainers로 MySQL을 띄우므로 Docker가 켜져 있어야 한다.
- MUST: DB를 쓰는 테스트는 `@Import(TestcontainersConfiguration.class)`로 실제 MySQL에서 돌린다. H2 같은 대체 DB를 쓰지 않는다.

PR을 올린 뒤에는 Codex Code Review가 아래 `Code Review Rules`로 한 번 더 리뷰한다. AI 리뷰는 병합 조건이 아니다. 병합은 사람이 Codex 리뷰의 P0, P1 지적이 처리됐는지 확인하고 한다.

### 문서만 바꾼 작업

- 빌드는 생략해도 된다. 링크와 번호가 맞는지 확인한다.

## 스킬

| 스킬 | 언제 쓰나 |
|---|---|
| `code-review` | PR을 올리기 전에 내 변경을 리뷰할 때 |
| `api-evidence` | API PR에 호출 결과(스크린샷, 응답 텍스트)를 남길 때 |
| `pr-create` | PR을 만들 때. 안에서 `code-review`를 먼저 실행한다 |

스킬을 고칠 때:

1. 원본 `.agents/skills/<이름>/SKILL.md`를 고친다.
2. `bash scripts/sync-agent-skills.sh`로 `.claude/skills/`에 복사한다.
3. 두 폴더가 다르면 CI(`agent-skills-sync`)가 실패한다.

- MUST NOT: `.claude/skills/`를 직접 고치지 않는다.

## Code Review Rules

로컬 리뷰(스킬 `code-review`)와 PR 리뷰(Codex Code Review)가 함께 쓰는 기준이다.

### 리뷰 작성 방식

- MUST: 리뷰의 모든 문장을 한국어로 쓴다. 코드, 경로, 에러 코드는 원래 글자 그대로 둔다.
- MUST: 존댓말 `~습니다`, `~세요`로 짧게 쓴다.
- MUST: 지적 하나는 아래 순서로 쓴다.
  1. 첫 줄: 등급(`**P0**`, `**P1**`)과 문제를 한 문장으로
  2. `생기는 상황:` 어떤 입력이나 호출에서 무엇이 잘못되는지
  3. `고치는 방법:` 무엇을 어떻게 바꾸면 되는지
- MUST NOT: 칭찬, 인사, 감탄, 이모지를 쓰지 않는다.
- MUST NOT: "~일 수도 있습니다"처럼 확인하지 않은 추측을 지적으로 올리지 않는다.

좋은 예:

```markdown
**P1** 다른 사용자의 모임도 지울 수 있습니다.

생기는 상황: 모임장이 아닌 사용자가 `DELETE /api/v1/meetups/3`을 부르면 200이 납니다.

고치는 방법: `deleteMeetup`에서 모임장인지 확인하고 아니면 403 `MEETUP_FORBIDDEN`으로 답하세요.
```

### P0: 병합하면 안 되는 것

- 비밀값(비밀번호, API 키, 토큰, 웹후크 URL)이 코드, 설정, 테스트, 로그에 들어 있다.
- 로그인한 사용자가 다른 사용자의 자원을 조회하거나 바꿀 수 있다(권한 검사 누락).
- 응답에 다른 사용자의 개인 정보, 스택 트레이스, SQL이 나간다.
- 로그에 비밀값이나 다른 사용자의 개인 정보가 남는다. 서버 오류의 원인을 찾으려고 서버 로그에 스택 트레이스를 남기는 것은 지적하지 않는다.
- `prod` 프로필에서 Swagger, 상세 헬스체크처럼 내부 정보를 여는 설정이 켜진다.
- 데이터를 지우거나 덮어쓰는 동작이 명세에 없는데 들어 있다.

### P1: 고치고 병합해야 하는 것

- 동작이 `docs/spec/`, `docs/api/`와 다르다. 또는 API 모양이 바뀌었는데 `docs/api/`를 고치지 않았다.
- `docs/api-convention.md`의 MUST를 어긴다(응답 틀, 상태 코드, 에러 `code`, JSON 필드 이름, 주소 모양).
- API가 자기 영역이 아닌 데이터를 바꾼다. 예외는 문서에 이유가 적힌 경우뿐이다(예: 탈퇴 API, `docs/api/10-settings.md`).
- 여러 테이블을 바꾸는데 하나의 트랜잭션으로 묶이지 않아 중간에 실패하면 데이터가 어긋난다.
- dev에 병합된 마이그레이션 파일을 고쳤다. 또는 테이블을 마이그레이션이 아닌 방법으로 만들거나 바꿨다.
- 마이그레이션의 테이블 · 칼럼이 `docs/db/`와 다르고, 문서도 고치지 않았다.
- API를 추가하거나 바꿨는데 정상과 실패 경우의 통합 테스트가 없다.
- 테스트가 실제로 검증하지 않는다(단언이 없거나 항상 통과한다).

### 지적하지 않는 것

- 들여쓰기, 이름 짓기 같은 코드 스타일. 위 "백엔드 코드"에 적은 규칙만 지적하고, 나머지는 코드 규칙(KAN-4)을 정하기 전까지 지적하지 않는다.
- 취향 차이일 뿐인 다른 구현 방법.

## 브라우저 도구

스크린샷과 GitHub 업로드는 Playwright MCP로 한다. 설정은 `.mcp.json`(Claude Code)과 `.codex/config.toml`(Codex)에 있다.

- 브라우저 프로필과 스크린샷은 `.playwright-mcp/`에 저장된다. git에 올라가지 않는다.
- GitHub 로그인은 PC마다 처음 한 번 사람이 그 브라우저 창에서 직접 한다.
