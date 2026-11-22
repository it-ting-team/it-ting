# KAN-38 02 로그인 ①: 보안 기반과 로그인 · 로그아웃 · 내 계정 조회

- Jira: [KAN-38](https://it-ting.atlassian.net/browse/KAN-38)
- 브랜치: `feature/be/KAN-38-login-session`

## 계획

### 목표

02 로그인 · 회원가입을 PR 4개로 나눈 것 가운데 첫 PR이다. 로그인이 필요한 API가 함께 쓸 보안 기반을 만들고, 아래 API 3개를 연다.

- `POST /api/v1/sessions` ([API 명세](../api/02-auth.md#post-apiv1sessions))
- `DELETE /api/v1/sessions/current` ([API 명세](../api/02-auth.md#delete-apiv1sessionscurrent))
- `GET /api/v1/me` ([API 명세](../api/02-auth.md#get-apiv1me))

| PR | 내용 |
|---|---|
| **① 이 작업** | 보안 기반, `user` · `user_profile` · `user_suspension` 테이블, 로그인 · 로그아웃 · 내 계정 조회 |
| ② | 이메일 가입 4 API(이메일 인증 코드, Mattermost 인증 코드, 계정 만들기) |
| ③ | 비밀번호 재설정 3 API |
| ④ | Google 로그인 2 API |

범위 밖:
- 계정을 만드는 API는 ②에서 만든다. 이 PR의 테스트와 Swagger 호출은 DB에 직접 넣은 계정을 쓴다.
- `nextStep`의 `NEW_TERM`과, 온보딩 · 새 학기를 마치지 않은 계정을 막는 일(`ONBOARDING_INCOMPLETE`, `NEW_TERM_INCOMPLETE`)은 땅굴 · 학기 테이블과 막을 API가 생기는 03 이후에 한다.

### 정한 것

| # | 질문 | 정한 답 | 정한 사람 |
|---|---|---|---|
| 1 | CSRF · CORS | 세션 쿠키를 `SameSite=Lax`로 주고 CORS는 허용 목록만 연다. CSRF 토큰은 두지 않는다. 프론트와 백엔드를 다른 사이트로 나누지 않는다 | 사용자 (2026-11-22) |
| 2 | 세션 유지 기간 | 마지막으로 쓴 뒤 14일. 요청할 때마다 다시 14일로 늘어난다 | 사용자 (2026-11-22) |
| 3 | 세션 저장 | Spring Security + Spring Session(Redis) | Claude, 반대 없음 |
| 4 | 쿠키 이름 | 세션 `ITTING_SESSION`, 가입 진행 `ITTING_SIGNUP`(②) | Claude, 반대 없음 |
| 5 | 비밀번호 해시 | BCrypt, `password_hash` `VARCHAR(100)` | Claude, 반대 없음 |
| 6 | `user` 칼럼 길이 | `email` · `google_email` `VARCHAR(254)`, `google_sub` `VARCHAR(255)`, `mattermost_id` `VARCHAR(64)` | Claude, 반대 없음 |
| 7 | `user_suspension.reason` | `VARCHAR(200)` | Claude, 반대 없음 |
| 8 | 로그인하지 않고 없는 `/api/**` 주소를 부를 때 | `404`가 아니라 `401 UNAUTHENTICATED`(아래 B). `docs/api-convention.md`에 적는다 | 사용자 (2026-11-22) |

### 이번에 Claude가 정하는 것 (확인 부탁)

| # | 무엇 | 정한 답 | 까닭 |
|---|---|---|---|
| A | 로그인 없이 부를 수 있는 주소 | **목록에 적은 것만 연다.** 나머지 `/api/**`는 모두 로그인이 필요하다 | 새 API에 로그인 검사를 깜빡해도 막혀 있게 한다. 열어 두고 하나씩 막는 방식은 빠뜨리면 P0이 된다 |
| B | 로그인하지 않고 없는 `/api/**` 주소를 부를 때 | `404`가 아니라 **`401 UNAUTHENTICATED`**. 로그인한 사용자에게는 지금처럼 `404`다. `docs/api-convention.md`에 적는다 | A의 결과다. 주소가 있는지 보기 전에 로그인을 검사한다. 로그인하지 않은 사람에게 어떤 주소가 있는지 알리지 않는 효과도 있다 |
| C | `nextStep` | `user.onboarded_at`이 비어 있으면 `ONBOARDING`, 있으면 `HOME`. `NEW_TERM`은 땅굴 입구 · 학기 테이블이 생길 때 넣는다 | 온보딩을 마치는 API(03)가 아직 없어 지금은 모든 계정이 `ONBOARDING`이다 |
| D | 쿠키 속성 | `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`, `Max-Age` 14일. 세션이 있는 요청마다 쿠키를 다시 줘 브라우저의 만료도 14일씩 늘린다 | 브라우저를 닫아도 로그인이 유지된다. 서버 세션과 쿠키가 같은 때 끝난다. `localhost`는 http여도 브라우저가 `Secure` 쿠키를 받는다 |
| E | CORS 허용 출처 | 설정 `itting.cors.allowed-origins`. local은 `http://localhost:3000`, test · prod는 배포할 때 환경 변수로 넣는다 | |
| F | 세션 고정 공격 | 로그인에 성공하면 세션 id를 새로 만든다. 이미 로그인한 브라우저에서 다시 로그인하면 이전 세션을 버린다 | |
| G | 세션에 넣는 값 | 계정 id만. 세션의 주인 이름도 계정 id로 둔다 | ③ 비밀번호 재설정의 "그 계정의 세션을 모두 지운다"를 Spring Session의 주인 이름 색인으로 한다 |
| H | 로그인 실패의 응답 시간 | 없는 이메일이어도 BCrypt 비교를 한 번 해서, 응답 시간으로 가입 여부를 알 수 없게 한다 | 명세의 "어느 쪽이 틀렸는지 알리지 않는다"를 시간으로도 지킨다 |
| I | 정지 기록이 여러 개 겹칠 때 | 지금 정지 중인 기록 가운데 가장 늦게 끝나는 것의 사유와 끝 시각을 준다 | |
| J | `GET /me`인데 세션의 계정이 없거나 탈퇴한 경우 | 세션을 지우고 `401 UNAUTHENTICATED` | 정상 흐름에서는 탈퇴 · 정지 때 세션을 지우지만(10 · 11), 놓쳐도 로그인이 남지 않게 한다 |
| K | 패키지 | 계정 엔티티 · 저장소는 `com.itting.user`, 로그인 API · 서비스와 보안 설정은 `com.itting.auth`. 에러 코드는 `AuthErrorCode`(`LOGIN_FAILED`, `ACCOUNT_SUSPENDED`) | `user`는 03 ~ 10이 함께 읽는다 |
| L | `user_profile`의 미정 칸 | `profile_image_url` `VARCHAR(500)` | 파일 저장소를 정할 때(04) 더 길어야 하면 새 마이그레이션으로 늘린다 |
| M | 테스트의 Redis | Testcontainers로 `redis:7.4`(compose와 같은 이미지) | DB와 같이 실제 Redis에서 돌린다 |

### 만들 것

| 파일 | 내용 |
|---|---|
| `build.gradle` | Spring Security, Spring Session Redis, Redis, 테스트용 Security · Testcontainers Redis |
| `db/migration/V3__create_user.sql` | `user`, `user_profile`, `user_suspension` |
| `user/domain/` | `User`, `UserProfile`, `UserSuspension`, `UserRole`, `UserStatus` |
| `user/repository/` | 계정 · 프로필 · 정지 저장소 |
| `auth/config/` | `SecurityConfig`(허용 목록, CORS, CSRF 끔, 401 · 403을 응답 틀로), 세션 쿠키 설정 |
| `auth/controller/SessionController` | `POST /sessions`, `DELETE /sessions/current` |
| `auth/controller/MeController` | `GET /me` |
| `auth/service/` | `LoginService`, `MeService`, `NextStep` 계산 |
| `auth/dto/` | `LoginRequest`, `LoginResponse`, `MeResponse`, `SuspensionDetail` |
| `auth/error/AuthErrorCode` | `LOGIN_FAILED`, `ACCOUNT_SUSPENDED` |
| `application*.yml` | 세션 14일, 쿠키, Redis 접속, CORS 허용 출처 |
| 테스트 | 아래 표 |
| 문서 | `docs/db/account.md`(미정 칸), `docs/api/02-auth.md`(구현 단계로 넘긴 것 중 정한 것), `docs/api-convention.md`(세션 기간 · CSRF · CORS, B) |

### 테스트

| API | 정상 | 실패 |
|---|---|---|
| `POST /sessions` | 맞는 이메일 · 비밀번호면 `201`, `userId` · `nextStep`, `ITTING_SESSION` 쿠키(속성 포함), 그 쿠키로 `GET /me` 성공 | 비밀번호 틀림 · 없는 이메일 · Google로만 가입한 계정은 `401 LOGIN_FAILED`. 정지 중이면 `403 ACCOUNT_SUSPENDED` + `detail`. 해제됐거나 끝난 정지는 로그인됨. 비밀번호가 틀리면 정지 중이어도 `LOGIN_FAILED`. 이메일 형식이 틀리면 `400 INVALID_INPUT` |
| `DELETE /sessions/current` | `200`, 쿠키를 지우는 헤더, 그 뒤 같은 쿠키로 `GET /me`는 `401` | 로그인하지 않으면 `401 UNAUTHENTICATED` |
| `GET /me` | 소속(캠퍼스 이름 · 기수 · 층 · `campusFloorId`)과 `nextStep`. `onboarded_at`이 있으면 `HOME` | 로그인하지 않으면 `401`. 세션의 계정이 탈퇴했으면 `401` |
| 보안 기반 | 허용 목록의 주소(캠퍼스 · 기수 목록, Swagger, 헬스체크)는 로그인 없이 됨. CORS 허용 출처의 미리 보내는 요청(OPTIONS)은 허용 헤더를 받음 | 허용하지 않은 출처는 CORS가 거절됨. 로그인하지 않고 없는 `/api/**` 주소는 `401` |

## 결과

### 바꾼 것

| 파일 | 내용 |
|---|---|
| `build.gradle` | `spring-boot-starter-security`, `-data-redis`, `-session-data-redis`, 테스트용 `-security-test` |
| `db/migration/V3__create_user.sql` | `user`, `user_profile`, `user_suspension`(UNIQUE · FK 포함) |
| `user/domain/` | `User`, `UserProfile`, `UserSuspension`, `UserRole`, `UserStatus` |
| `user/repository/` | `UserRepository.findByEmail`, `UserProfileRepository`, `UserSuspensionRepository.findActive`(지금 정지 중, 늦게 끝나는 것부터) |
| `global/security/SecurityConfig` | 허용 목록 `PUBLIC_API` 밖의 `/api/**`는 로그인 필요, CSRF · 폼 로그인 · 기본 로그아웃 끔, 요청 저장 끔(로그인하지 않은 사람에게 세션을 만들지 않음), CORS, BCrypt |
| `global/security/SessionCookieConfig` | 세션 쿠키 `ITTING_SESSION`(`HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`, `Max-Age` = `spring.session.timeout`) |
| `global/security/SessionCookieRefreshFilter` | 세션이 살아 있는 요청마다 쿠키를 다시 줘 14일 연장 |
| `global/security/SecurityErrorWriter` | 보안 필터가 막은 요청의 401 · 403을 응답 틀로 |
| `global/security/CorsProperties` | `itting.cors.allowed-origins` |
| `global/security/ApiCorsProcessor` | CORS가 거절한 요청도 응답 틀의 `403 FORBIDDEN`으로 |
| `auth/controller/` | `SessionController`(`POST /sessions`, `DELETE /sessions/current`), `MeController`(`GET /me`) |
| `auth/service/` | `LoginService`(비밀번호 · 정지 확인), `SessionService`(세션 만들기 · 지우기), `MeService` |
| `auth/domain/` | `NextStep`, `AuthErrorCode`(`LOGIN_FAILED`, `ACCOUNT_SUSPENDED`) |
| `auth/dto/` | `LoginRequest`, `LoginResponse`, `MeResponse`, `SuspensionDetailResponse` |
| `application*.yml` | 세션 14일, 색인 세션 저장소, local의 Redis 포트 · CORS 출처, test · prod의 환경 변수 안내 |
| `docs/api-convention.md` | 로그인 절에 쿠키 이름 · 14일 · 허용 목록 · 세션 id 새로 만들기, "CSRF · CORS" 절, 로그인하지 않은 없는 주소는 `401` |
| `docs/api/02-auth.md` | "구현하며 정한 것" 절, 구현 단계로 넘기는 것에서 세션 쿠키 이름을 뺌 |
| `docs/db/account.md` | `user` · `user_profile` · `user_suspension`의 미정 길이, 이메일의 대소문자 비교 |

### 확인한 것

- `./gradlew build` 통과. 테스트 61개(기존 34 + 새 27).
  - `SessionApiTests`(14): 로그인 201 · 쿠키 속성 · 그 쿠키로 `/me`, Redis에 계정 id로 색인 · 14일, 이메일 대소문자, 온보딩 마친 계정은 `HOME`, 비밀번호 틀림 · 없는 이메일 · Google로만 가입 · 72바이트 넘는 비밀번호는 같은 `LOGIN_FAILED`이고 쿠키 없음, 탈퇴 계정, 정지 중이면 `ACCOUNT_SUSPENDED` + 가장 늦게 끝나는 정지, 비밀번호가 틀리면 정지 중이어도 `LOGIN_FAILED`, 끝남 · 해제 · 예정 정지는 막지 않음, 형식 오류 400, 다시 로그인하면 새 세션 id와 이전 세션 무효, 로그아웃 200 · 쿠키 지움 · 그 세션 무효, 로그아웃은 지금 기기만, 로그인하지 않은 로그아웃 401
  - `MeApiTests`(6): 소속과 `nextStep`, 온보딩 마친 계정 `HOME`, 요청마다 쿠키 14일 연장, 로그인하지 않음 401, 없는 세션 401, 탈퇴한 계정의 세션은 지우고 401
  - `SecurityConfigTests`(6): 허용 목록 · Swagger · 헬스체크는 로그인 없이, 로그인하지 않은 없는 주소 401, 로그인하면 404, 로그인하지 않은 요청엔 세션을 만들지 않음, 허용 출처 CORS 허용, 다른 출처 거절
  - `SessionCookieServerTests`(1): 내장 서버를 띄워 HTTP로 불러도 쿠키가 하나만, 같은 속성으로 나감
  - `GlobalExceptionHandlerTests`: 로그인이 필요한 주소가 됐으므로 로그인한 사용자(`@WithMockUser`)로 부름
- `git merge-tree --write-tree HEAD origin/dev`: 충돌 없음
- 로컬 코드 리뷰: P0 0건, P1 0건. 확인 필요 1건(실제 서버의 쿠키 설정)은 `SessionCookieServerTests`를 더해 확인함
- Codex 리뷰 P1 1건 고침: 허용하지 않은 출처를 CORS가 거절할 때 일반 텍스트(`Invalid CORS request`)로 답하던 것을 응답 틀의 `403 FORBIDDEN`으로(`ApiCorsProcessor`). 거절 테스트가 본문도 확인한다

### 계획과 달라진 점

- 보안 설정은 모든 영역이 함께 쓰므로 `auth/config/`가 아니라 `global/security/`에 뒀다. 에러 코드는 패키지 규칙(controller, service, repository, domain, dto)에 맞춰 `auth/domain/AuthErrorCode`에 뒀다.
- 정지 `detail`의 DTO 이름은 규칙에 맞춰 `SuspensionDetailResponse`로 했다.
- 쿠키 속성을 `application.yml`의 `server.servlet.session.cookie.*`가 아니라 `SessionCookieConfig` 코드에 뒀다. 스프링 부트가 그 설정을 내장 서버를 띄울 때만 적용해, 테스트(MockMvc)에서는 기본 쿠키 `SESSION`이 나갔다.
- 테스트 트랜잭션 안에서 SQL로 계정 상태를 바꾼 뒤 영속성 컨텍스트를 비우게 했다(`TestUsers`). 비우지 않으면 앞선 API 호출이 읽어 둔 계정이 그대로 보인다.
- 실제 서버 테스트(`SessionCookieServerTests`)를 더했다.

### 남은 일

- Swagger 호출 스크린샷과 응답 텍스트(스킬 `api-evidence`). 로컬 DB에 로그인용 계정을 SQL로 넣어야 한다.
- 배포할 Redis가 `CONFIG` 명령을 막으면(관리형 Redis) 서버가 뜰 때 실패한다. 색인 세션 저장소가 키 만료 알림을 켜려고 부르기 때문이다. 그때는 `spring.session.data.redis.configure-action: none`과 Redis 쪽 설정으로 바꾼다.
- 정지 · 탈퇴 때 그 계정의 세션을 모두 지우는 일은 정지(11)와 탈퇴(10) API에서 한다.

### 검토 순서

1. `docs/api-convention.md`, `docs/api/02-auth.md`, `docs/db/account.md`
2. `V3__create_user.sql` → `user/domain/`
3. `global/security/SecurityConfig` → `SessionCookieConfig` → `SessionCookieRefreshFilter` → `SecurityErrorWriter`
4. `SessionController` → `LoginService` → `SessionService`
5. `MeController` → `MeService`
6. 테스트: `SessionApiTests`, `MeApiTests`, `SecurityConfigTests`, `SessionCookieServerTests`
