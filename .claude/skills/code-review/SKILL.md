---
name: code-review
description: PR을 올리기 전에 지금 브랜치의 변경을 AGENTS.md의 Code Review Rules로 리뷰하고, P0 · P1 지적을 고친다. pr-create가 PR 전에 반드시 부르며, "리뷰해 줘", "코드 리뷰" 같은 요청에서도 쓴다.
---

# 로컬 코드 리뷰

지금 브랜치가 `origin/dev`에서 바꾼 내용을 `AGENTS.md`의 `## Code Review Rules` 기준으로 리뷰한다. PR을 올린 뒤에는 Codex Code Review가 같은 기준으로 한 번 더 본다.

## 하지 말 것

- MUST NOT: 기준에 없는 코드 스타일(들여쓰기, 이름, 패키지 구조)을 지적하지 않는다.
- MUST NOT: 확인하지 않은 추측을 P0, P1로 올리지 않는다. 확신이 없으면 "확인 필요"로 따로 적는다.
- MUST NOT: 지적을 고치면서 리뷰 대상이 아닌 코드를 함께 바꾸지 않는다.

## 절차

### 1. 리뷰 대상 모으기

```bash
git fetch origin
git diff --stat origin/dev...HEAD
git diff origin/dev...HEAD
```

- 커밋하지 않은 변경이 있으면 `git diff`와 `git diff --staged`도 함께 본다.

### 2. 기준 문서 읽기

1. `AGENTS.md`의 `## Code Review Rules`를 읽는다.
2. 변경이 API나 기능이면 해당 영역 문서를 읽는다.
   - `docs/spec/<번호>-*.md`
   - `docs/api/<번호>-*.md`
   - `docs/db/<영역>.md`
   - `docs/api-convention.md`

### 3. 리뷰하기

변경된 파일마다 아래를 확인한다.

1. P0 목록에 해당하는 것이 있는가.
2. P1 목록에 해당하는 것이 있는가.
3. 바뀐 코드에서 호출하는 쪽, 호출되는 쪽을 따라가 본다. diff만 보지 않는다.
4. 지적마다 실제로 문제가 생기는 상황(입력 → 잘못된 결과)을 한 줄로 적을 수 있는지 확인한다. 못 적으면 "확인 필요"로 내린다.

### 4. 결과 보고

말투와 언어는 `AGENTS.md`의 `리뷰 작성 방식`을 따른다. 아래 형식으로 사용자에게 보여 준다.

```markdown
## 로컬 리뷰 결과

| 등급 | 위치 | 문제 | 생기는 상황 |
|---|---|---|---|
| P0 | `backend/.../MeetupService.java:42` | 모임장이 아닌 사람도 모임을 지울 수 있음 | 다른 사용자가 `DELETE /api/v1/meetups/3` 호출 → 200 |
| P1 | `docs/api/07-meetup.md` | 응답에 `hostId`가 추가됐는데 문서에 없음 | 프론트가 문서만 보고 필드를 모름 |

확인 필요:
- <확신이 없는 것>
```

- 지적이 없으면 "P0, P1 없음"이라고 적는다.

### 5. 고치기

1. P0과 P1을 고친다.
2. 테스트와 빌드를 다시 돌린다(`backend/`면 `./gradlew build`).
3. 고친 뒤 1단계부터 다시 리뷰한다. P0, P1이 없을 때까지 반복한다.
4. 커밋은 사용자가 요청한 경우에만 한다.

### 6. PR 본문에 남기기

PR의 `## 확인한 것` 절에 한 줄로 적는다.

```markdown
- 로컬 코드 리뷰: P0 0건, P1 2건 고침(권한 검사 추가, API 문서 갱신)
```

## 끝났는지 확인

- [ ] `origin/dev` 대비 모든 변경 파일을 봤다.
- [ ] P0, P1이 남아 있지 않다.
- [ ] 고친 뒤 테스트와 빌드가 통과했다.
