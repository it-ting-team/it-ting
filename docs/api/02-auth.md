# 로그인 · 회원가입 API

이메일 가입, SSAFY 인증, 로그인과 로그아웃, Google 로그인, 비밀번호 재설정을 다룬다. 규칙은 [API 공통 규칙](../api-convention.md)을 따른다.

- 명세: [02 로그인 · 회원가입](../spec/02-auth.md), 로그아웃은 [10 설정 · 계정](../spec/10-settings.md#설정--로그아웃-확인)
- DB 설계: [계정 · 프로필 · 관심사 · 질문](../db/account.md)

## 목록

| 메서드 | 주소 | 하는 일 | 로그인 | 명세의 화면 · 항목 |
|---|---|---|---|---|
| `POST` | `/api/v1/email-verifications` | 가입할 이메일로 인증 코드를 보낸다 | 불필요 | 회원가입 1 · 3, 4 |
| `POST` | `/api/v1/email-verifications/check` | 이메일 인증 코드가 맞는지 확인한다 | 불필요 | 회원가입 1 · 5, 7 |
| `POST` | `/api/v1/users` | 이메일 계정을 만들고 로그인한다 | 불필요 | 회원가입 2 · 5 |
| `POST` | `/api/v1/me/ssafy-verifications` | Mattermost로 SSAFY 인증 코드를 보낸다 | 필요 | 회원가입 3 · 4 |
| `POST` | `/api/v1/me/ssafy-verifications/complete` | 인증 코드를 확인하고 소속과 약관 동의를 저장한다 | 필요 | 회원가입 3 · 3, 5, 7, 8 |
| `POST` | `/api/v1/sessions` | 이메일과 비밀번호로 로그인한다 | 불필요 | 로그인 2 ~ 4 |
| `DELETE` | `/api/v1/sessions/current` | 로그아웃한다 | 필요 | 10 설정 – 로그아웃 확인 2 |
| `GET` | `/api/v1/me` | 로그인 상태와 다음에 갈 화면을 알려 준다 | 필요 | 영역 규칙 "로그인한 뒤 가는 곳" |
| `GET` | `/api/v1/oauth/google` | Google 로그인을 시작한다(브라우저 이동) | 불필요 | 로그인 5 |
| `GET` | `/api/v1/oauth/google/callback` | Google이 돌려보내는 주소. 로그인시키고 프론트엔드로 보낸다 | 불필요 | 로그인 5 |
| `POST` | `/api/v1/password-resets` | 비밀번호 재설정 메일을 보낸다 | 불필요 | 재설정 메일 요청 2, 메일 보냄 2 |
| `GET` | `/api/v1/password-resets/{token}` | 재설정 링크가 유효한지 확인한다 | 불필요 | 링크 만료 1 |
| `POST` | `/api/v1/password-resets/{token}/complete` | 새 비밀번호를 저장한다 | 불필요 | 새 비밀번호 설정 3 |

## 영역 규칙

### 다음에 갈 화면

로그인하거나 가입 단계를 마치면 응답의 `nextStep`으로 다음에 갈 화면을 알려 준다. 프론트엔드는 이 값만 보고 이동한다. `nextStep`은 enum이고 값은 아래 셋이다.

| `nextStep` | 계정 상태 | 가는 곳 |
|---|---|---|
| `SSAFY_VERIFICATION` | SSAFY 인증을 마치지 않음 | 회원가입 3 |
| `ONBOARDING` | 온보딩을 마치지 않음 | 온보딩 1 |
| `HOME` | 가입을 모두 마침 | 내 땅굴 |

### 가입을 마치지 않은 계정

`nextStep`이 `HOME`이 아닌 계정은 이 문서의 API, 온보딩 API, [공통 API](00-common.md) 가운데 "가입 중에도"라고 적힌 것만 부를 수 있다. 다른 API를 부르면 `403`과 `SIGNUP_INCOMPLETE`로 답한다.

### 인증 코드

- 이메일 인증과 SSAFY 인증 모두 숫자 6자리다.
- 코드를 보내는 API는 코드가 만료되는 시각 `expiresAt`을 준다. 프론트엔드는 이 값으로 남은 시간을 표시한다.
- 코드를 다시 보내는 API는 따로 없다. 같은 API를 다시 부르면 새 코드를 보내고 이전 코드는 쓸 수 없게 된다.
- 재전송 간격 안에 다시 부르면 `429`와 `TOO_MANY_REQUESTS`로 답한다.

## POST /api/v1/email-verifications

가입할 이메일로 인증 코드를 보낸다. 처음 보낼 때와 다시 보낼 때 모두 이 API를 쓴다.

- 로그인: 불필요
- 명세: 회원가입 1 · 3, 4

### 요청

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| 본문 | `email` | 문자열 | ✓ | 가입할 이메일 |

### 응답 `data`

`201`로 답한다.

| 이름 | 타입 | 설명 |
|---|---|---|
| `expiresAt` | 시각 | 인증 코드가 만료되는 시각 |

### 에러

| 상태 | `code` | 때 |
|---|---|---|
| `409` | `EMAIL_DUPLICATED` | 이미 가입한 이메일 |

## POST /api/v1/email-verifications/check

입력한 인증 코드가 맞는지 확인한다. 코드는 계정을 만들 때 한 번 더 확인하므로 여기서는 없애지 않는다.

- 로그인: 불필요
- 명세: 회원가입 1 · 5, 7

### 요청

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| 본문 | `email` | 문자열 | ✓ | 코드를 받은 이메일 |
| 본문 | `code` | 문자열 | ✓ | 숫자 6자리 |

### 응답 `data`

`null`이다.

### 에러

| 상태 | `code` | 때 |
|---|---|---|
| `400` | `VERIFICATION_CODE_MISMATCH` | 코드가 맞지 않음 |
| `400` | `VERIFICATION_CODE_EXPIRED` | 코드가 만료됐거나 보낸 적이 없음 |

## POST /api/v1/users

이메일 계정을 만든다. 성공하면 세션 쿠키를 주어 로그인 상태가 된다. "비밀번호 확인" 칸은 프론트엔드가 비교하고 서버로 보내지 않는다.

- 로그인: 불필요
- 명세: 회원가입 2 · 5

### 요청

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| 본문 | `email` | 문자열 | ✓ | 인증한 이메일 |
| 본문 | `code` | 문자열 | ✓ | 그 이메일로 받은 인증 코드 |
| 본문 | `password` | 문자열 | ✓ | [00 공통](../spec/00-common.md) 9의 비밀번호 규칙을 따른다 |

### 응답 `data`

`201`로 답한다.

| 이름 | 타입 | 설명 |
|---|---|---|
| `userId` | 숫자 | 만든 계정의 id |
| `nextStep` | enum | 항상 `SSAFY_VERIFICATION` |

### 에러

| 상태 | `code` | 때 |
|---|---|---|
| `400` | `VERIFICATION_CODE_MISMATCH` | 코드가 맞지 않음 |
| `400` | `VERIFICATION_CODE_EXPIRED` | 코드가 만료됐거나 보낸 적이 없음 |
| `409` | `EMAIL_DUPLICATED` | 이미 가입한 이메일 |

## POST /api/v1/me/ssafy-verifications

고른 기수의 Mattermost로 인증 코드를 보낸다. 처음 보낼 때와 다시 보낼 때 모두 이 API를 쓴다. Mattermost ID가 틀려 메시지가 가지 않아도 같은 응답을 준다(명세는 화면 문구로 ID를 다시 확인하라고 안내한다).

- 로그인: 필요
- 명세: 회원가입 3 · 4

### 요청

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| 본문 | `generation` | 숫자 | ✓ | 기수 |
| 본문 | `mattermostId` | 문자열 | ✓ | SSAFY Mattermost ID |

### 응답 `data`

`201`로 답한다.

| 이름 | 타입 | 설명 |
|---|---|---|
| `expiresAt` | 시각 | 인증 코드가 만료되는 시각 |

### 에러

| 상태 | `code` | 때 |
|---|---|---|
| `409` | `SSAFY_ALREADY_VERIFIED` | 이미 SSAFY 인증을 마친 계정 |
| `409` | `MATTERMOST_ID_DUPLICATED` | 다른 계정이 인증에 쓴 Mattermost ID |

## POST /api/v1/me/ssafy-verifications/complete

인증 코드를 확인하고, 기수 · 캠퍼스-층 · 약관 동의를 한 번에 저장한다. 기수와 Mattermost ID는 코드를 보낼 때 받은 값을 쓴다.

- 로그인: 필요
- 명세: 회원가입 3 · 3, 5, 7, 8

### 요청

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| 본문 | `code` | 문자열 | ✓ | Mattermost로 받은 숫자 6자리 |
| 본문 | `campusFloorId` | 숫자 | ✓ | 고른 캠퍼스-층의 id |
| 본문 | `hasAgreedTerms` | 참/거짓 | ✓ | 이용약관 동의. `true`여야 한다 |
| 본문 | `hasAgreedPrivacy` | 참/거짓 | ✓ | 개인정보 수집 · 이용 동의. `true`여야 한다 |

### 응답 `data`

| 이름 | 타입 | 설명 |
|---|---|---|
| `userId` | 숫자 | 계정의 id |
| `nextStep` | enum | 항상 `ONBOARDING` |

### 에러

| 상태 | `code` | 때 |
|---|---|---|
| `400` | `VERIFICATION_CODE_MISMATCH` | 코드가 맞지 않음 |
| `400` | `VERIFICATION_CODE_EXPIRED` | 코드가 만료됐거나 보낸 적이 없음 |
| `404` | `CAMPUS_FLOOR_NOT_FOUND` | 없는 캠퍼스-층 |
| `409` | `SSAFY_ALREADY_VERIFIED` | 이미 SSAFY 인증을 마친 계정 |
| `409` | `MATTERMOST_ID_DUPLICATED` | 그 사이 다른 계정이 같은 Mattermost ID로 인증함 |

## POST /api/v1/sessions

이메일과 비밀번호로 로그인한다. 성공하면 세션 쿠키를 준다.

- 로그인: 불필요
- 명세: 로그인 2 ~ 4

### 요청

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| 본문 | `email` | 문자열 | ✓ | 로그인 이메일 |
| 본문 | `password` | 문자열 | ✓ | 비밀번호 |

### 응답 `data`

`201`로 답한다.

| 이름 | 타입 | 설명 |
|---|---|---|
| `userId` | 숫자 | 계정의 id |
| `nextStep` | enum | [다음에 갈 화면](#다음에-갈-화면) |

```json
{
  "success": true,
  "data": { "userId": 31, "nextStep": "ONBOARDING" },
  "error": null
}
```

### 에러

| 상태 | `code` | 때 |
|---|---|---|
| `401` | `LOGIN_FAILED` | 이메일이나 비밀번호가 맞지 않음. 어느 쪽이 틀렸는지는 알리지 않는다 |
| `403` | `ACCOUNT_SUSPENDED` | 정지된 계정. `error.detail`에 사유와 끝나는 시각을 담는다 |

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ACCOUNT_SUSPENDED",
    "message": "정지된 계정입니다.",
    "fields": null,
    "detail": { "reason": "신고 누적", "endsAt": "2026-11-25T08:00:00Z" }
  }
}
```

## DELETE /api/v1/sessions/current

로그아웃한다. 지금 쓰는 세션을 지운다.

- 로그인: 필요
- 명세: 10 설정 – 로그아웃 확인 2

### 요청

없다.

### 응답 `data`

`null`이다.

### 에러

공통 에러 코드만 쓴다.

## GET /api/v1/me

로그인한 계정과 다음에 갈 화면을 알려 준다. 프론트엔드는 처음 열릴 때 이 API로 로그인 여부를 확인한다. 로그인하지 않았으면 `401`과 `UNAUTHENTICATED`로 답한다.

- 로그인: 필요
- 명세: 영역 규칙 "로그인한 뒤 가는 곳"

### 요청

없다.

### 응답 `data`

| 이름 | 타입 | 설명 |
|---|---|---|
| `userId` | 숫자 | 계정의 id |
| `nextStep` | enum | [다음에 갈 화면](#다음에-갈-화면) |

### 에러

공통 에러 코드만 쓴다.

## GET /api/v1/oauth/google

Google 로그인을 시작한다. JSON을 주고받는 API가 아니라 브라우저가 이 주소로 이동한다. 서버는 Google 로그인 화면으로 다시 보낸다. 응답 틀을 쓰지 않는다.

- 로그인: 불필요
- 명세: 로그인 5

## GET /api/v1/oauth/google/callback

Google이 로그인을 마친 브라우저를 돌려보내는 주소다. 프론트엔드가 직접 부르지 않는다. 응답 틀을 쓰지 않는다.

- 로그인: 불필요
- 명세: 로그인 5

서버가 하는 일:

1. 처음 쓰는 Google 계정이면 계정을 만든다.
2. 세션을 만들고 세션 쿠키를 준다.
3. 프론트엔드의 정해진 주소로 브라우저를 보낸다. 프론트엔드는 `GET /api/v1/me`의 `nextStep`으로 갈 곳을 정한다.

로그인하지 못했으면(정지된 계정, Google에서 취소) 세션을 만들지 않고, 프론트엔드 주소에 `error=<code>`를 붙여 보낸다.

## POST /api/v1/password-resets

비밀번호 재설정 메일을 보낸다. 가입하지 않은 이메일이거나 Google로만 가입한 계정이어도 똑같이 성공으로 답한다. 다시 부르면 이전 링크는 쓸 수 없게 된다.

- 로그인: 불필요
- 명세: 재설정 메일 요청 2, 메일 보냄 2

### 요청

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| 본문 | `email` | 문자열 | ✓ | 가입한 이메일 |

### 응답 `data`

`201`로 답한다. `data`는 `null`이다.

### 에러

공통 에러 코드만 쓴다.

## GET /api/v1/password-resets/{token}

메일의 링크로 들어왔을 때 링크가 아직 유효한지 확인한다. 유효하면 새 비밀번호 설정 화면을, 아니면 링크 만료 화면을 보여준다.

- 로그인: 불필요
- 명세: 링크 만료 1

### 요청

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| 경로 | `token` | 문자열 | ✓ | 메일의 링크에 든 값 |

### 응답 `data`

`null`이다.

### 에러

| 상태 | `code` | 때 |
|---|---|---|
| `404` | `PASSWORD_RESET_LINK_INVALID` | 30분이 지났거나, 이미 썼거나, 없는 링크 |

## POST /api/v1/password-resets/{token}/complete

새 비밀번호를 저장하고 링크를 만료시킨다. 로그인시키지 않는다. 프론트엔드는 로그인 화면으로 보낸다.

- 로그인: 불필요
- 명세: 새 비밀번호 설정 3

### 요청

| 위치 | 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|---|
| 경로 | `token` | 문자열 | ✓ | 메일의 링크에 든 값 |
| 본문 | `password` | 문자열 | ✓ | [00 공통](../spec/00-common.md) 9의 비밀번호 규칙을 따른다 |

### 응답 `data`

`null`이다.

### 에러

| 상태 | `code` | 때 |
|---|---|---|
| `404` | `PASSWORD_RESET_LINK_INVALID` | 30분이 지났거나, 이미 썼거나, 없는 링크 |

## 명세에 없어 이 문서에서 정한 것

- **이미 가입한 이메일**: 인증 코드를 요청할 때 `EMAIL_DUPLICATED`로 알린다. 비밀번호 찾기와 달리 가입 여부가 드러난다.
- **계정이 만들어지는 때**: 회원가입 2에서 비밀번호를 정할 때 만들고 바로 로그인 상태가 된다. DB 설계의 A4와 같다.
- **비밀번호를 재설정한 뒤**: 그 계정의 로그인 세션을 모두 지운다.

## 구현 단계로 넘기는 것

- 인증 코드의 유효 시간과 재전송 간격(명세에서 제안 상태다)
- Google 로그인을 마친 뒤 돌아갈 프론트엔드 주소
- 정지된 계정이 Google로 로그인했을 때 사유와 기간을 프론트엔드에 전하는 방법
