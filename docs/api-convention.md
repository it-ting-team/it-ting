# API 공통 규칙

사람과 AI 에이전트 모두 이 규칙을 따른다. API를 설계하거나 구현하기 전에 이 문서를 읽는다.

무엇을 만드는지는 [서비스 명세](spec/README.md), 무엇을 저장하는지는 [DB 설계](db/README.md)가 기준이다. 이 문서는 그 둘을 API로 옮길 때의 공통 약속만 다룬다.

## 주소

```text
/api/v1/<자원>[/{id}][/<딸린 자원>[/{id}]]
```

- MUST: 모든 주소는 `/api/v1`로 시작한다.
- MUST: 자원은 영어 소문자 복수형 명사로 쓴다. 낱말이 둘 이상이면 하이픈으로 잇는다(`chat-rooms`).
- MUST: 하나뿐인 자원은 단수형으로 쓴다(`/me/profile`, `/posts/{postId}/bookmark`).
- MUST: 딸린 자원은 부모 아래에 쓴다(`/api/v1/meetups/{meetupId}/applications`).
- MUST: 로그인한 사용자 자신의 자원은 `/api/v1/me` 아래에 쓴다. 주소에 자기 id를 넣지 않는다.
- MUST: 필터, 정렬, 검색은 쿼리 파라미터로 받는다. 이름은 camelCase다.
- MUST NOT: 주소에 동사를 쓰지 않는다(`/getMeetup`, `/meetups/create`).

수락, 거절, 취소처럼 자원의 상태를 바꾸는 행동은 예외로 `POST /<자원>/{id}/<행동>`을 쓴다. 대상이 id로 정해지지 않으면 `{id}` 없이 쓴다.

```text
POST /api/v1/chat-requests/{chatRequestId}/accept
POST /api/v1/meetups/{meetupId}/cancel
POST /api/v1/email-verifications/check
```

## 메서드

| 메서드 | 쓰임 | 예 |
|---|---|---|
| `GET` | 조회 | `GET /api/v1/meetups/12` |
| `POST` | 만들기, 상태를 바꾸는 행동 | `POST /api/v1/meetups` |
| `PATCH` | 일부 수정 | `PATCH /api/v1/meetups/12` |
| `PUT` | 켜기 | `PUT /api/v1/meetups/12/like` |
| `DELETE` | 삭제, 끄기 | `DELETE /api/v1/meetups/12/like` |

- MUST: 켜고 끄는 것(좋아요, 찜, 저장, 단골)은 `PUT`으로 켜고 `DELETE`로 끈다. 이미 켜진 것을 다시 켜도 성공으로 답한다.
- MUST NOT: 한 번 부를 때마다 켜짐과 꺼짐이 뒤바뀌는 API를 만들지 않는다.
- MUST NOT: `GET`으로 데이터를 바꾸지 않는다.

## 응답 틀

모든 응답 본문은 같은 틀을 쓴다.

```json
{
  "success": true,
  "data": { "id": 12, "title": "알고리즘 스터디", "capacity": 6 },
  "error": null
}
```

- MUST: 본문은 항상 `success`, `data`, `error` 세 필드를 가진다.
- MUST: 성공하면 `success`는 `true`, `error`는 `null`이다. 돌려줄 값이 없으면 `data`는 `null`이다.
- MUST: 실패하면 `success`는 `false`, `data`는 `null`이고 `error`에 내용을 담는다.
- MUST: `success`는 HTTP 상태 코드와 맞춘다. 2xx일 때만 `true`다.
- MUST: 목록은 `data`에 배열을 바로 넣지 않고 `data.items`에 넣는다.
- MUST NOT: 실패를 `200`으로 답하지 않는다.
- MUST NOT: 본문이 없는 `204`를 쓰지 않는다.

브라우저를 다른 주소로 보내는 것으로 답하는 주소(Google 로그인)는 이 틀을 쓰지 않는다.

## 상태 코드

| 코드 | 때 |
|---|---|
| `200` | 조회, 수정, 삭제, 행동이 성공함 |
| `201` | 새 자원을 만듦. `data`에 만든 자원을 담는다 |
| `400` | 입력값이 형식이나 범위에 맞지 않음 |
| `401` | 로그인하지 않았거나 로그인이 만료됨 |
| `403` | 로그인했지만 할 수 없는 일임 |
| `404` | 주소나 대상이 없음 |
| `409` | 지금 상태에서는 할 수 없음(중복, 정원 참, 이미 처리됨) |
| `429` | 너무 자주 요청함 |
| `500` | 서버 오류 |

## 에러

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "NICKNAME_DUPLICATED",
    "message": "이미 사용 중인 닉네임입니다.",
    "fields": null,
    "detail": null
  }
}
```

| 필드 | 내용 |
|---|---|
| `code` | 프론트엔드가 실패 종류를 구분하는 값 |
| `message` | 개발자가 읽는 한국어 설명 한 문장 |
| `fields` | 입력 오류일 때만 어느 필드가 왜 틀렸는지의 목록. 그 밖에는 `null` |
| `detail` | 화면에 보여 줄 값이 실패에 딸려 있을 때만 그 값을 담은 객체(예: 계정 정지의 사유와 끝나는 시각). 그 밖에는 `null` |

- MUST: `code`는 영어 대문자와 밑줄로만 쓰고 `<대상>_<상태>` 모양으로 짓는다(`MEETUP_NOT_FOUND`, `MEETUP_FULL`). 숫자는 쓰지 않는다.
- MUST: 같은 실패에는 어느 API에서나 같은 `code`를 쓴다.
- MUST: 화면에 보여 줄 문구는 프론트엔드가 `code`를 보고 정한다.
- MUST NOT: 프론트엔드가 `message`의 글자로 실패를 구분하지 않는다.
- MUST NOT: `message`에 스택 트레이스, SQL, 다른 사용자의 정보를 넣지 않는다.

입력 오류는 `400`과 `INVALID_INPUT`으로 답하고 `fields`를 채운다. `field`는 요청 JSON의 필드 이름이다.

```json
"error": {
  "code": "INVALID_INPUT",
  "message": "입력값이 올바르지 않습니다.",
  "fields": [
    { "field": "nickname", "message": "12자를 넘을 수 없습니다." }
  ],
  "detail": null
}
```

`detail`에 무엇을 담는지는 그 `code`를 쓰는 API의 문서에 적는다.

### 공통 에러 코드

| 상태 | `code` | 때 |
|---|---|---|
| `400` | `INVALID_INPUT` | 입력값이 형식이나 범위에 맞지 않음 |
| `401` | `UNAUTHENTICATED` | 로그인하지 않았거나 로그인이 만료됨 |
| `403` | `FORBIDDEN` | 로그인했지만 할 수 없는 일임 |
| `404` | `NOT_FOUND` | 없는 주소임 |
| `429` | `TOO_MANY_REQUESTS` | 너무 자주 요청함 |
| `500` | `INTERNAL_ERROR` | 서버 오류 |

대상이 없을 때는 `NOT_FOUND` 대신 `<대상>_NOT_FOUND`를 쓴다. 영역마다의 코드는 그 영역의 API 목록에 적는다.

## JSON 필드

- MUST: 필드 이름은 camelCase로 쓴다(`createdAt`, `hostId`). DB 칼럼은 snake_case이고 API에서 바꾼다.
- MUST: id는 숫자로 준다.
- MUST: 시각은 UTC의 ISO 8601 형식으로 주고 이름은 `…At`으로 끝낸다(`"createdAt": "2026-11-18T08:01:05Z"`).
- MUST: 날짜만 있는 값은 `YYYY-MM-DD`로 준다.
- MUST: 상태와 종류는 DB와 같은 대문자 코드 문자열로 준다(`"status": "PENDING"`).
- MUST: 참과 거짓은 `is`, `has`, `can`으로 시작한다(`isLiked`, `hasNext`).
- MUST: 값이 없으면 필드를 빼지 않고 `null`로 준다. 빈 목록은 `[]`로 준다.
- MUST NOT: "3분 전", "신청 대기" 같은 화면 표현을 서버가 만들어 주지 않는다. 시간대 변환과 문구는 프론트엔드가 맡는다.

## 목록

명세의 화면이 페이지 번호로 넘기면 페이지 방식을, "더 보기"나 스크롤로 이어 붙이면 커서 방식을 쓴다.

### 페이지 방식

```text
GET /api/v1/posts?page=1&size=20
```

```json
"data": {
  "items": [],
  "page": 1,
  "size": 20,
  "totalItems": 134,
  "totalPages": 7
}
```

- MUST: `page`는 1부터 센다. 보내지 않으면 1이다.
- MUST: `size`의 기본값은 명세에 적힌 개수를 따른다.

### 커서 방식

```text
GET /api/v1/notifications?size=20
GET /api/v1/notifications?size=20&cursor=eyJpZCI6ODEyfQ
```

```json
"data": {
  "items": [],
  "nextCursor": "eyJpZCI6ODEyfQ",
  "hasNext": true
}
```

- MUST: 첫 요청에는 `cursor`를 보내지 않는다. 다음 요청에는 받은 `nextCursor`를 그대로 보낸다.
- MUST: 더 받을 것이 없으면 `hasNext`는 `false`, `nextCursor`는 `null`이다.
- MUST NOT: 프론트엔드가 `nextCursor`의 내용을 해석하거나 직접 만들지 않는다.

## 로그인

세션 방식을 쓴다. 로그인하면 서버가 세션을 만들어 Redis에 저장하고 세션 쿠키를 준다. 이메일 로그인과 Google 로그인 모두 같은 세션을 만든다.

- MUST: 세션 쿠키는 `HttpOnly`, `Secure`, `SameSite=Lax`로 준다.
- MUST: 프론트엔드는 요청에 쿠키를 함께 보낸다(`credentials: "include"`).
- MUST: 로그인이 필요한 API에 세션이 없거나 만료됐으면 `401`과 `UNAUTHENTICATED`로 답한다.
- MUST: 계정이 정지되거나 탈퇴하면 그 계정의 세션을 모두 지운다.
- MUST NOT: 로그인 정보를 응답 본문의 토큰이나 `localStorage`로 주고받지 않는다.

## 영역별 API 목록

구현한 API의 기준 문서는 Swagger(springdoc)가 만든 문서다. 구현하기 전의 설계는 `docs/api/<번호>-<영역>.md`로 쓴다. 번호는 서비스 명세의 문서 번호와 같다. [템플릿](api/_template.md)을 복사해서 시작하고, 문서 목록은 [API 명세](api/README.md)에 둔다.

## AI 에이전트 작업 절차

1. 명세에서 해당 화면의 항목을, DB 설계에서 관련 테이블을 확인한다.
2. `주소`와 `메서드` 규칙에 따라 주소와 메서드를 정한다.
3. 요청과 응답의 필드를 `JSON 필드` 규칙에 따라 정한다. 목록이면 `목록`에서 방식을 고른다.
4. 실패하는 경우마다 상태 코드와 `code`를 정한다. 이미 있는 `code`가 있으면 그것을 쓴다.
5. 아래 정규식과 `예시`의 나쁜 예로 검사한다.

## 검사 정규식

주소는 쿼리 파라미터를 떼고, id 자리를 `{meetupId}`처럼 이름으로 쓴 모양으로 검사한다.

| 대상 | 정규식 |
|---|---|
| 주소 | `^/api/v1(/([a-z][a-z0-9]*(-[a-z0-9]+)*\|\{[a-z][a-zA-Z0-9]*\}))+$` |
| 에러 `code` | `^[A-Z]+(_[A-Z]+)*$` |
| JSON 필드 이름 | `^[a-z][a-zA-Z0-9]*$` |
| 시각 | `^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$` |

## 예시

좋은 예:

```text
GET    /api/v1/meetups?page=1&size=20
POST   /api/v1/meetups/12/applications
PATCH  /api/v1/me/profile
PUT    /api/v1/posts/31/bookmark
POST   /api/v1/chat-requests/5/accept
```

나쁜 예:

| 내용 | 문제 |
|---|---|
| `GET /api/v1/getMeetupList` | 주소에 동사 사용 |
| `GET /api/v1/meetup/12` | 단수형 자원 |
| `GET /api/v1/chat_rooms` | 밑줄 사용 |
| `POST /api/v1/meetups/12/like/toggle` | 켜짐과 꺼짐이 뒤바뀌는 API |
| `200`과 `"success": false` | 실패를 `200`으로 답함 |
| `"data": [ … ]` | 목록을 `items` 없이 배열로 줌 |
| `"created_at": "2026-11-18 17:01:05"` | snake_case 필드, UTC ISO 8601이 아님 |
| `"code": "U001"` | 뜻이 보이지 않는 코드 |
| `"statusText": "신청 대기"` | 화면 표현을 서버가 만듦 |

## 이 문서가 다루지 않는 것

- 영역별 개별 API. 각 영역을 구현하기 직전에 쓴다.
- 실시간 통신(채팅, 알림)과 파일 업로드 방식.
- 세션이 유지되는 기간, CSRF 방어 방법, 허용하는 출처(CORS). 백엔드 보안 설정을 할 때 정한다.
