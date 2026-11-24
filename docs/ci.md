# CI

PR마다 GitHub Actions(`.github/workflows/ci.yml`)가 빌드 · 테스트와 저장소 규칙을 검사한다. 결과는 마지막 요약 job `ci` 하나로 모인다.

지금은 `ci`가 필수 검사가 아니다. 빠르게 개발하는 동안 병합을 기다리게 하지 않으려는 것이다. `ci`가 실패해도 병합 버튼은 눌리지만, 실패한 PR은 고친 뒤 병합한다. 필수 검사로 거는 때는 아래 "나중에 할 일".

## 검사 목록

| job | 언제 도나 | 무엇을 보나 |
|---|---|---|
| 바뀐 곳 찾기 | 늘 | `backend/`나 `.github/workflows/`가 바뀌었는지 |
| 백엔드 빌드 · 테스트 | 위 경로가 바뀐 PR, `dev`에 병합된 뒤 | `backend/`에서 `./gradlew build`. 테스트는 Testcontainers로 MySQL · Redis를 띄운다 |
| 병합 흐름 | PR | `test`로 가는 PR은 `dev`에서만, `main`으로 가는 PR은 `test`나 `hotfix/*`에서만 |
| 마이그레이션 보호 | PR | 기준 브랜치에 이미 있는 `backend/src/main/resources/db/migration/` 파일을 고치거나 지우거나 이름을 바꿨는지 |
| 비밀값 | PR | gitleaks로 PR의 새 커밋에 비밀값(API 키, 토큰, 웹후크 URL 등)이 들었는지 |
| 스킬 동기화 | PR | `.agents/skills/`와 `.claude/skills/`가 같은지 |
| `ci` | 늘 | 위 job이 모두 통과하거나 건너뛰었는지 |

- 문서만 바꾼 PR은 백엔드 빌드를 건너뛴다. 건너뛴 job은 통과로 본다.
- 같은 PR에 새 커밋을 푸시하면 이전 실행은 취소되고 새로 돈다.
- `dev`에 병합된 뒤 백엔드 빌드가 실패하면 PR 알림과 같은 Discord 채널로 알린다. 다른 PR이 같이 병합되며 깨진 경우다. 빨리 고치는 PR을 올린다.

## 실패했을 때

PR 아래 Checks에서 빨간 job을 눌러 로그를 본다. `ci`가 빨강이면 그 위의 어느 job이 실패했는지 `ci` 로그 첫 줄들에 나온다.

| job | 고치는 법 |
|---|---|
| 백엔드 빌드 · 테스트 | 로컬 `backend/`에서 `./gradlew build`로 같은 실패를 본다. 실행 페이지 아래 Artifacts의 `backend-test-reports`를 받으면 테스트 보고서(HTML)가 있다 |
| 병합 흐름 | PR의 기준 브랜치를 바꾼다(Edit → base). 작업 브랜치는 `dev`로 보낸다. `docs/branch-strategy.md` |
| 마이그레이션 보호 | 기존 파일을 되돌리고, 고칠 내용은 새 번호의 마이그레이션으로 더한다. `AGENTS.md`의 DB 절 |
| 비밀값 | 아래 "비밀값이 잡혔을 때" |
| 스킬 동기화 | 원본 `.agents/skills/`를 고친 뒤 `bash scripts/sync-agent-skills.sh`를 실행해 커밋한다 |

### 비밀값이 잡혔을 때

로그에 규칙 이름(`RuleID`), 파일, 줄, 커밋이 나온다. 값은 가려서 나온다.

1. 진짜 비밀값이면 먼저 그 값을 폐기하고 새로 발급한다(웹후크는 지우고 다시 만든다). 공개 저장소라 푸시한 순간 노출된 것으로 본다. 커밋에서 지우는 것만으로는 부족하다.
2. 새 값은 GitHub Secrets나 `.env`에 넣고, 코드에는 환경 변수 이름만 둔다.
3. 커밋에서 값을 뺀다. 이미 푸시한 커밋에 있으면 PR의 새 커밋 범위에 계속 남으므로, 작업 브랜치의 이력을 고쳐 강제 푸시해야 한다. 강제 푸시는 사람이 판단해 직접 한다.
4. 비밀값이 아닌데 잡혔으면(공개 키, 테스트용 예시 값) 그 줄 끝에 `gitleaks:allow` 주석을 단다.

## 로컬에서 돌리는 법

저장소 루트에서:

```bash
# 병합 흐름: <기준 브랜치> <원본 브랜치>
bash scripts/ci/check-merge-flow.sh test dev

# 마이그레이션 보호: <기준>
git fetch origin
bash scripts/ci/check-migrations.sh origin/dev

# 비밀값: gitleaks v8.30.1을 받아서(https://github.com/gitleaks/gitleaks/releases)
gitleaks git --config .gitleaks.toml --log-opts="origin/dev..HEAD" --redact

# 백엔드
cd backend && ./gradlew build
```

## 처음 한 번 (저장소 관리자)

CI를 만든 PR(KAN-26)이 병합된 뒤 관리자가 GitHub에서 한다.

1. **Secret scanning · Push protection 켜기**: Settings → Advanced Security(또는 Code security)에서 Secret scanning과 Push protection을 켠다. 공개 저장소라 무료다. Push protection은 비밀값이 든 푸시를 GitHub가 받기 전에 막는다. CI의 gitleaks는 푸시 뒤에 도는 안전망이다.
2. **`test` 브랜치 보호**: `test` 브랜치가 생기면 규칙 세트 `protect-main-dev`의 대상(Target branches)에 `test`를 더한다.

### 나중에 할 일: 필수 검사 `ci` 걸기

몇 주 써 보고 빌드 시간이 견딜 만하면 건다. 늦어도 테스트 서버 자동 배포(KAN-63) 전에 건다. 깨진 코드가 `test`로 가 배포되지 않게 하려는 것이다.

- Settings → Rules → Rulesets → `protect-main-dev` → "Require status checks to pass"를 켜고 `ci`를 더한다. `ci`가 한 번 이상 돌아야 목록에 나온다.
- 건 뒤에는 `docs/branch-strategy.md`의 보호 규칙과 `AGENTS.md`의 완료 기준을 "통과해야 병합된다"로 고친다.

### Push protection이 웹후크 URL을 잡는지 확인

GitHub가 알아보는 비밀값 종류는 서비스마다 다르다. 웹후크 URL을 막는지 직접 확인한다.

1. 새 작업 브랜치에서 임시 파일에 가짜 Discord 웹후크 URL을 넣고 커밋한다. 모양은 `https://discord.com/api/webhooks/<18자리 숫자>/<68자 영문 · 숫자 · _ · ->`다. 값은 무작위로 만든다. `abcdef…`, `123456…`처럼 뻔한 값은 gitleaks와 GitHub 모두 예시로 보고 건너뛴다.
2. 푸시해 본다. Push protection이 막으면 된다. 막지 못하면 CI의 gitleaks가 PR에서 잡는다.
3. 커밋을 지우고(`git reset --hard HEAD~1`) 브랜치를 지운다. 가짜 값이 원격에 올라갔으면 그 브랜치를 원격에서도 지운다.

## 나중에 더할 것

| 검사 | 언제 |
|---|---|
| Docker 이미지 빌드 | Dockerfile이 생기는 KAN-63 |
| 프론트엔드 린트 · 타입 검사 · 빌드 | `frontend/`가 생기는 KAN-25 |
| 코드 스타일 | 코드 규칙(KAN-4)을 정한 뒤 |
