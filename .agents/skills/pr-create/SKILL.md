---
name: pr-create
description: 작업 브랜치를 점검하고 dev로 가는 PR을 만든다. 브랜치 이름, 커밋 메시지, 서명, dev와의 충돌, 빌드를 확인한 뒤 정해진 제목과 본문 형식으로 PR을 연다. "PR 만들어 줘", "PR 올리자" 같은 요청에서 쓴다.
---

# PR 만들기

작업 브랜치를 `dev`로 병합하는 PR을 만든다. 이 스킬은 사용자가 PR을 요청했을 때만 쓴다.

PR 제목과 본문 형식은 지금까지 쓴 방식을 따른 것이다. KAN-5(PR 작성 규칙)에서 규칙을 정하면 이 스킬을 그에 맞춘다.

## 하지 말 것

- MUST NOT: `main`이나 `dev`로 직접 푸시하지 않는다.
- MUST NOT: 강제 푸시하지 않는다. 필요하면 이유와 명령을 사용자에게 보여 주고 사용자가 실행하게 한다.
- MUST NOT: 서명이 없거나 규칙에 맞지 않는 커밋을 고치려고 사용자의 git 설정을 바꾸지 않는다.
- MUST NOT: PR을 병합하지 않는다. 병합은 사람이 한다.

## 절차

### 1. 브랜치 점검

1. 브랜치 이름이 `docs/branch-strategy.md`의 형식과 맞는지 확인한다.

   ```text
   ^(feature|fix|chore|docs|hotfix)/(fe|be|infra|common)/KAN-[0-9]+-[a-z0-9-]+$
   ```

2. 작업 트리가 깨끗한지 확인한다(`git status --short`가 비어 있음).
   - 커밋하지 않은 변경이 있으면 사용자에게 알리고 멈춘다.

### 2. 커밋 점검

1. 이번 PR에 들어갈 커밋을 본다.

   ```bash
   git fetch origin
   git log --format='%G? %h %s' origin/dev..HEAD
   ```

2. 모든 커밋 제목이 `docs/commit-convention.md`의 정규식과 맞는지 확인한다.

   ```regex
   ^(feat|fix|refactor|test|docs|chore): (?!.*KAN-\d)\S.{0,48}[^.]$
   ```

3. 서명 상태는 첫 글자로 판단한다.
   - `G`: 서명이 확인됐다.
   - `E`: 서명은 있지만 이 PC에 확인용 키 목록(`gpg.ssh.allowedSignersFile`)이 없어 확인을 못 한 것이다. 통과로 본다.
   - `N`: 서명이 없는 것이다. 하나라도 있으면 멈추고, `docs/commit-convention.md`의 "서명 없는 커밋이 이미 있는 작업 브랜치" 절을 사용자에게 안내한다.
4. 제목과 본문 사이에 빈 줄이 있는지 확인한다. 없으면 본문이 제목에 붙어 버린다.

### 3. dev와 충돌 확인

```bash
git merge-tree --write-tree HEAD origin/dev
```

- 출력에 `CONFLICT`가 있으면 충돌 파일을 사용자에게 보여 주고 멈춘다.

### 4. 로컬 코드 리뷰

- 코드가 바뀌었으면 스킬 `code-review`를 실행한다.
- P0, P1이 남아 있으면 PR을 만들지 않고 멈춘다.
- 문서만 바뀌었으면 생략한다.

### 5. 빌드

- `backend/`가 바뀌었으면 `backend/`에서 `./gradlew build`를 실행해 통과하는지 확인한다.
- 문서만 바뀌었으면 생략한다.

### 6. 푸시

```bash
git push -u origin HEAD
```

### 7. PR 만들기

제목:

```text
<유형>: KAN-<번호> <설명>
```

- 유형: 대표 커밋의 유형
- 번호: 브랜치 이름의 티켓 번호
- 설명: 한국어로 짧게

예: `chore: KAN-23 Spring Boot 프로젝트 초기 설정`

본문은 아래 형식으로 쓴다. 해당하지 않는 절은 뺀다.

```markdown
## 무엇을 바꿨나

<바꾼 것과 이유. 표로 정리해도 좋다>

## 쓰는 법

<실행 방법, 주소 등. 사람이 직접 해 볼 것이 있을 때만>

## 확인한 것

- <실행한 빌드, 테스트, 직접 확인한 결과>
- 로컬 코드 리뷰: <스킬 code-review의 결과 한 줄>

## 호출 결과

<API를 추가하거나 바꿨으면 스킬 api-evidence로 채운다>

## 나중에 정할 것

- <이번 범위 밖으로 남긴 것>

<AI 공동 작성 표시: 도구가 정한 문구>
```

PR을 연다.

```bash
gh pr create --base dev --title "<제목>" --body-file <본문 파일>
```

### 8. 마무리

- API를 추가하거나 바꿨으면 이어서 스킬 `api-evidence`를 실행한다.
- PR 주소를 사용자에게 알린다.
- Codex Code Review가 PR에 리뷰를 남기면 사용자에게 알린다. P0, P1 지적은 같은 브랜치에서 고친다.

## 끝났는지 확인

- [ ] 브랜치 이름, 커밋 제목, 서명이 규칙에 맞는다.
- [ ] dev와 충돌이 없다.
- [ ] 코드가 바뀌었으면 로컬 코드 리뷰에서 P0, P1이 없다.
- [ ] 백엔드가 바뀌었으면 빌드가 통과했다.
- [ ] PR의 base가 `dev`다.
- [ ] API PR이면 `## 호출 결과` 절이 채워졌다.
