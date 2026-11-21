---
name: api-evidence
description: API를 추가하거나 바꾼 PR에 호출 결과를 남긴다. 서버를 띄워 Swagger UI에서 호출하고, 화면 스크린샷과 응답 텍스트를 PR 본문의 "호출 결과" 절에 넣는다. API PR을 마무리할 때, 또는 "호출 결과 붙여 줘", "스크린샷 남겨 줘" 같은 요청에서 쓴다.
---

# API 호출 결과 남기기

API를 추가하거나 바꾼 PR의 본문에 `## 호출 결과` 절을 만든다. 이 절에는 Swagger 호출 화면 스크린샷과 응답 텍스트가 들어간다. `AGENTS.md`의 완료 기준 2, 3에 해당한다.

## 시작 조건

- PR이 이미 열려 있다. 없으면 스킬 `pr-create`로 먼저 만든다.
- Playwright MCP 도구(`browser_navigate`, `browser_click`, `browser_take_screenshot`, `browser_file_upload`, `browser_evaluate`)를 쓸 수 있다. 없으면 멈추고 사용자에게 MCP 설정을 확인해 달라고 한다(`README.md`의 AI 에이전트 절).

## 하지 말 것

- MUST NOT: 비밀번호를 입력하지 않는다. GitHub에 로그인돼 있지 않으면 멈추고 사람에게 로그인을 부탁한다.
- MUST NOT: GitHub 댓글을 등록하지 않는다. 댓글 칸은 이미지 주소를 얻는 데만 쓰고 비운다.
- MUST NOT: 사용자가 띄운 서버를 끄지 않는다.
- MUST NOT: 응답에 든 토큰, 비밀번호, 실제 이메일, 내 PC 경로를 그대로 올리지 않는다. `<redacted>`나 `...`로 줄인다.

## 절차

### 1. 호출할 목록 정하기

1. `git diff origin/dev...HEAD -- backend/src/main`에서 추가 · 변경된 엔드포인트를 찾는다.
2. 엔드포인트마다 두 가지를 고른다.
   - 정상 경우 1개
   - 대표 실패 경우 1개 (검증 오류, 권한 없음, 없는 대상 가운데 해당하는 것)
3. 목록을 사용자에게 보여 준다.

### 2. 서버 띄우기

1. 포트를 확인한다. `http://localhost:8080/actuator/health`가 응답하면 이미 떠 있는 서버다.
   - 그 서버가 지금 브랜치 코드인지 모르면 쓰지 않는다.
   - 대신 다른 포트로 띄운다.
2. 필요하면 `docker compose up -d`로 DB와 Redis를 먼저 띄운다.
3. 서버를 백그라운드로 띄운다.

   ```bash
   cd backend
   ./gradlew bootRun --args='--server.port=8081'
   ```

4. `curl -s http://localhost:<포트>/actuator/health`가 `"status":"UP"`을 줄 때까지 기다린다.

### 3. Swagger에서 호출하고 찍기

엔드포인트와 경우마다 반복한다.

1. `browser_navigate`로 `http://localhost:<포트>/swagger-ui.html`을 연다.
2. 해당 operation을 펼치고 `Try it out`을 누른다.
3. 파라미터와 본문을 넣고 `Execute`를 누른다.
4. 응답 영역(`Server response`)이 보일 때까지 기다린다.
5. `browser_take_screenshot`으로 해당 operation 블록만 찍는다.
   - 대상: 그 operation 요소(`element`, `ref` 지정)
   - 파일 이름: `.playwright-mcp/shots/<브랜치 마지막 부분>-<순번>-<operation 이름>-<ok|fail>.png`
   - 예: `.playwright-mcp/shots/KAN-41-meetup-create-1-createMeeting-ok.png`
6. 같은 요청을 `curl -s -i`로 다시 보내 상태 코드와 응답 본문을 텍스트로 얻는다.

### 4. 스크린샷을 GitHub에 올려 주소 얻기

1. `gh pr view --json url -q .url`로 PR 주소를 얻는다.
2. `browser_navigate`로 PR 페이지를 연다.
3. 로그인 상태를 확인한다. 페이지에 `Sign in` 버튼이 보이면 로그인이 안 된 상태다.
   - 멈추고 사람에게 이렇게 부탁한다: "열린 브라우저 창에서 GitHub에 로그인해 주세요".
   - 로그인했다고 하면 다시 시작한다.
4. 페이지 맨 아래 새 댓글 칸의 첨부 영역(`Paste, drop, or click to add files`)을 `browser_click`으로 누른다. 파일 선택 창이 열린 상태가 된다.
5. `browser_file_upload`로 3단계의 PNG 파일들을 넣는다.
6. 댓글 칸에 `https://github.com/user-attachments/assets/` 주소가 파일 수만큼 생길 때까지 기다린다.
7. `browser_evaluate`로 댓글 칸의 값을 읽는다.

   ```js
   document.querySelector('textarea[name="comment[body]"]').value
   ```

8. 읽은 값에서 `<img ... src="https://github.com/user-attachments/assets/...">` 줄들을 얻는다.
9. 댓글 칸을 비운다. 등록 버튼은 누르지 않는다.

   ```js
   const ta = document.querySelector('textarea[name="comment[body]"]');
   Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set.call(ta, '');
   ta.dispatchEvent(new Event('input', { bubbles: true }));
   ```

### 5. PR 본문에 넣기

1. `gh pr view --json body -q .body > <임시 파일>`로 지금 본문을 받는다.
2. `## 호출 결과` 절을 아래 형식으로 만든다.
   - 이미 있으면 그 절을 바꾼다.
   - 없으면 `## 확인한 것` 절 바로 다음에 넣는다.
   - `## 확인한 것`도 없으면 AI 표시 줄 바로 앞에 넣는다.
3. `gh pr edit --body-file <임시 파일>`로 올린다.
4. `gh pr view --json body`로 `user-attachments` 주소가 들어갔는지 확인한다.

### 6. 정리

- 2단계에서 내가 띄운 서버만 끈다.
- `browser_close`로 브라우저를 닫는다.

## 호출 결과 절 형식

````markdown
## 호출 결과

`<프로필>` 프로필, `<브랜치>`의 `<커밋 짧은 해시>`로 띄워 호출했습니다.

| 요청 | 경우 | 결과 |
|---|---|---|
| `POST /api/v1/meetings` | 정상 | 201 |
| `POST /api/v1/meetings` | 제목 없음 | 400 `INVALID_INPUT` |

### `POST /api/v1/meetings`: 정상

<img width="..." alt="createMeeting-ok" src="https://github.com/user-attachments/assets/..." />

<details>
<summary>요청과 응답</summary>

```http
POST /api/v1/meetings
Content-Type: application/json

{"title":"스터디","capacity":4}
```

```json
HTTP 201
{"id":1,"title":"스터디","capacity":4}
```

</details>
````

## 끝났는지 확인

- [ ] 1단계 목록의 모든 경우에 스크린샷과 응답 텍스트가 있다.
- [ ] PR 본문에 `user-attachments` 이미지가 경우 수만큼 있다.
- [ ] 비밀값과 개인 정보가 가려져 있다.
- [ ] 댓글이 등록되지 않았다.
- [ ] 내가 띄운 서버와 브라우저를 닫았다.
