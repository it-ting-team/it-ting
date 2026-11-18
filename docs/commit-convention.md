# 커밋 메시지 규칙

사람과 AI 에이전트 모두 이 규칙을 따른다. 커밋을 만들기 전에 이 문서를 읽는다.

## 형식

```text
<유형>: <설명>

<본문(선택)>

<AI 공동 작성 표시(해당 시)>
```

## 제목 규칙

- MUST: 첫 줄을 `<유형>: <설명>` 형식으로 쓴다.
- MUST: 설명을 한국어로 50자 이내로 쓴다.
- MUST: 설명을 `추가`, `수정`, `삭제`처럼 변경 내용을 나타내는 말로 끝낸다.
- MUST NOT: 설명 끝에 마침표를 찍지 않는다.
- MUST NOT: 티켓 번호와 영역(`fe`, `be`, `infra`, `common`)을 쓰지 않는다. [브랜치 이름](branch-strategy.md)에서 구분한다.

## 유형 선택

위에서부터 순서대로 확인하고, 처음 해당하는 유형을 쓴다.

| 순서 | 조건 | 유형 |
|---|---|---|
| 1 | 문서만 변경 | `docs` |
| 2 | 테스트 코드만 변경 | `test` |
| 3 | 잘못된 동작을 고침 | `fix` |
| 4 | 새로운 기능이나 동작을 추가 | `feat` |
| 5 | 동작은 그대로 두고 코드 구조만 개선 | `refactor` |
| 6 | 설정, 환경 구성, 빌드, CI, 그 외 | `chore` |

## 본문

- 제목만으로 변경 이유를 알 수 없을 때만 쓴다.
- 제목 아래 한 줄을 비우고 쓴다.
- 코드 변경 내용을 나열하지 말고 변경 이유를 쓴다.

## 커밋 단위

- MUST: 한 커밋에는 하나의 변경 목적만 담는다. 목적이 둘 이상이면 커밋을 나눈다.
- MUST NOT: 빌드나 테스트가 깨진 상태로 커밋하지 않는다.
- MUST NOT: 비밀번호, API 키, 웹훅 URL 같은 비밀값을 커밋하지 않는다.

## 작성자 정보

- MUST: `user.name`과 `user.email`에는 커밋하는 사람 본인의 정보를 쓴다.
- MUST: `user.email`은 본인 GitHub 계정에 인증된 이메일을 쓴다. GitHub이 주는 `noreply` 주소도 된다.
- MUST NOT: 다른 사람의 이름이나 이메일로 커밋하지 않는다. `--author`나 `GIT_AUTHOR_*`, `GIT_COMMITTER_*` 환경 변수로 바꾸는 것도 포함한다.
- MUST NOT: 다른 사람의 서명 키를 쓰거나 자신의 개인 키를 공유하지 않는다.
- MUST NOT: AI 에이전트는 사용자의 `user.name`, `user.email`, 서명 설정을 바꾸지 않고 `--no-gpg-sign`으로 서명을 건너뛰지 않는다.

함께 작업한 사람은 작성자를 바꾸지 말고 메시지 맨 아래에 `Co-Authored-By: <이름> <이메일>`로 표시한다.

## 커밋 서명

`main`과 `dev`에는 서명된 커밋만 병합된다([보호 규칙](branch-strategy.md#보호-규칙)). 컴퓨터마다 한 번 설정한다.

1. 서명용 키를 만든다.

   ```bash
   ssh-keygen -t ed25519 -f ~/.ssh/id_ed25519_signing -C "commit signing"
   ```

2. 모든 커밋에 서명하도록 설정한다.

   ```bash
   git config --global gpg.format ssh
   git config --global user.signingkey ~/.ssh/id_ed25519_signing.pub
   git config --global commit.gpgsign true
   ```

3. GitHub → Settings → SSH and GPG keys → New SSH key에서 Key type을 `Signing Key`로 고르고 `~/.ssh/id_ed25519_signing.pub`의 내용을 등록한다.
4. 같은 화면 맨 아래 Vigilant mode의 `Flag unsigned commits as unverified`를 켠다.
5. 커밋을 푸시한 뒤 GitHub 커밋 목록에서 `Verified` 표시를 확인한다.

### 서명 없는 커밋이 이미 있는 작업 브랜치

커밋을 다시 만들어 서명한 뒤 작업 브랜치에 강제 푸시한다. 자기 커밋만 든 브랜치에서 쓴다.

```bash
git fetch origin
git rebase --no-ff origin/dev
git push --force-with-lease
```

## AI 공동 작성 표시

AI 에이전트가 작성에 참여한 커밋은 메시지 맨 아래에 `Co-Authored-By`를 쓴다.

```text
Co-Authored-By: <도구와 모델> <이메일>
```

## AI 에이전트 작업 절차

1. `git status`와 `git diff --staged`로 커밋할 변경을 확인한다.
2. 변경 목적이 둘 이상이면 커밋을 나눈다.
3. `유형 선택` 표에 따라 유형을 정한다.
4. 메시지를 작성하고 아래 정규식으로 첫 줄을 검사한다.
5. 사용자가 요청한 경우에만 커밋한다.

`.gitmessage` 템플릿은 `git commit -m`이나 `-F`를 쓰면 표시되지 않으므로, AI 에이전트는 이 문서를 기준으로 삼는다.

## 첫 줄 검사 정규식

```regex
^(feat|fix|refactor|test|docs|chore): (?!.*KAN-\d)\S.{0,48}[^.]$
```

병합 커밋은 검사 대상이 아니다.

## 예시

좋은 예:

```text
feat: 게시글 작성 API 추가
fix: 댓글 삭제 시 권한 검사 오류 수정
docs: 브랜치 전략 문서 추가
chore: 새 PR Discord 알림 워크플로 추가
```

나쁜 예:

| 메시지 | 문제 |
|---|---|
| `feat: KAN-30 게시글 작성 API 추가` | 티켓 번호 포함 |
| `feat(be): 게시글 작성 API 추가` | 영역 포함 |
| `update: 게시글 수정` | 허용되지 않은 유형 |
| `fix: 오류 수정.` | 끝에 마침표 사용 |

## 사람용 템플릿 설정

저장소를 클론한 뒤 한 번 실행하면 `git commit` 편집기에 [템플릿](../.gitmessage)이 표시된다.

```bash
git config commit.template .gitmessage
```
