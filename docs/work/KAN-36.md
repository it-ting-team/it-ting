# KAN-36 공통 응답 틀 · 예외 처리와 백엔드 패키지 규칙

- Jira: [KAN-36](https://it-ting.atlassian.net/browse/KAN-36)
- 브랜치: `feature/be/KAN-36-common-response`

## 계획

### 목표

모든 API가 [API 공통 규칙](../api-convention.md)의 응답 틀과 에러 모양으로 답하게 하는 공통 코드를 만든다. 영역 API를 만들기 전의 선행 작업이다.

### 범위

- 응답 틀 `{ success, data, error }`와 에러 `{ code, message, fields, detail }`
- 공통 에러 코드와 전역 예외 처리
- 영역 에러 코드를 정의하고 던지는 방법
- 패키지 규칙(KAN-4 코드 규칙의 최소 부분)

범위 밖: 로그인(`401 UNAUTHENTICATED`를 실제로 내는 일)은 02 로그인에서, `429`는 요청 제한을 만들 때 한다.

### 정한 것 (2026-11-22)

| # | 질문 | 정한 답 | 까닭 |
|---|---|---|---|
| 1 | 패키지를 어떻게 나누나 | 영역별 `com.itting.<영역>.{controller,service,repository,domain,dto}` | API 문서의 영역과 1:1로 맞고, 팀원이 영역을 나눠 맡아도 파일이 덜 겹친다 |
| 2 | DTO 이름 | `<무엇>Request` / `<무엇>Response`, `record` | |
| 3 | 규칙에 없던 실패 | `405 METHOD_NOT_ALLOWED`, `415 UNSUPPORTED_MEDIA_TYPE`을 공통 코드에 더한다 | HTTP 표준과 맞다 |

## 결과

### 바꾼 것

| 파일 | 내용 |
|---|---|
| `global/response/ApiResult.java` | 응답 틀. `ApiResult.ok(data)`, `ApiResult.ok()` |
| `global/response/ApiError.java` | 에러 모양과 `fields`의 한 줄(`field`, `message`) |
| `global/error/ErrorCode.java` | 영역 에러 코드 enum이 구현하는 인터페이스. 상수 이름이 `code` |
| `global/error/CommonErrorCode.java` | 공통 코드 8개. 상태 코드로 공통 코드를 찾는 `fromStatus` |
| `global/error/BusinessException.java` | 서비스가 실패를 알릴 때 던지는 예외. `detail`을 함께 실을 수 있다 |
| `global/error/GlobalExceptionHandler.java` | 예외를 응답 틀로 바꾼다(아래 표) |
| `global/error/ApiErrorController.java` | 컨트롤러 밖(필터, 서블릿)의 실패도 응답 틀로. 스프링 부트의 기본 오류 응답을 대신한다 |
| `global/config/JacksonConfig.java` | 시각을 초 단위 UTC(`2026-11-18T08:01:05Z`)로 준다 |
| `build.gradle` | `spring-boot-starter-validation` |
| `application.yml` | 검증 메시지를 한국어로 고정, 정적 파일 매핑 끄기 |
| `AGENTS.md` | "백엔드 코드" 절(패키지, DTO, 응답 · 예외 쓰는 법), 리뷰에서 지적하지 않는 것 수정, P0의 로그 줄을 응답과 로그로 나눔(서버 로그의 스택 트레이스는 지적하지 않음, 2026-11-22 결정) |
| `docs/api-convention.md` | 상태 코드와 공통 에러 코드에 `405`, `415`. 표에 없는 4xx는 `400 INVALID_INPUT` |

예외마다 나가는 응답:

| 예외 | 상태 · `code` | `fields` |
|---|---|---|
| `BusinessException` | 그 에러 코드의 상태와 이름 | `null`, `detail`은 실은 값 |
| `@Valid` 본문 · 폼 검증 실패 | `400 INVALID_INPUT` | `nickname`, `mole.head`, `answers[1].answer` 같은 경로 |
| 메서드 인자 검증 실패(`@RequestParam @Max`) | `400 INVALID_INPUT` | 파라미터 이름 |
| 쿼리 · 경로 값의 타입이 틀림, 필수 파라미터 없음 | `400 INVALID_INPUT` | 파라미터 이름 |
| 본문 필드의 타입이 틀림, enum 목록에 없는 값 | `400 INVALID_INPUT` | 그 필드의 경로 |
| 본문이 JSON이 아님 | `400 INVALID_INPUT` | `null` |
| 없는 주소(메서드와 상관없이) | `404 NOT_FOUND` | `null` |
| 허용하지 않는 메서드 | `405 METHOD_NOT_ALLOWED` + `Allow` 헤더 | `null` |
| 받지 않는 `Content-Type` | `415 UNSUPPORTED_MEDIA_TYPE` | `null` |
| 그 밖의 예외 | `500 INTERNAL_ERROR`. 예외 내용은 로그에만 남긴다 | `null` |

### 확인한 것

- `./gradlew build` 통과. 테스트 27개(기존 12 + 새 15).
  - `GlobalExceptionHandlerTests`(12개): 위 표의 경우마다 응답 본문 전체를 비교한다. 성공 응답의 `null` 필드와 빈 목록이 빠지지 않는다. 500 응답에 예외 메시지(SQL)가 나가지 않는다.
  - `CommonErrorCodeTests`(2개): 코드가 API 규칙의 정규식을 지키고, 상태 코드로 공통 코드를 찾는다.
  - `ItTingApplicationTests`: 정적 파일 매핑을 끈 뒤에도 Swagger 화면이 열린다.
- 로컬 코드 리뷰: P0 · P1 없음. 리뷰 규칙의 "로그에 스택 트레이스" 줄이 서버 오류 로그와 부딪혀 규칙 문장을 고쳤다.
- 로컬 서버(포트 18080)에서 직접 불러 봄: `GET`, `POST /api/v1/nothing` → `404 NOT_FOUND`, `DELETE /actuator/health` → `405` + `Allow: GET`, 헬스체크 `UP`, Swagger 화면 200.

### 계획과 달라진 점

- **정적 파일 매핑을 껐다.** 켜 두면 없는 주소에 `POST`를 보낼 때 정적 파일 처리기가 `405`로 답한다. API 서버라 정적 파일이 없고, Swagger 화면은 springdoc이 따로 준다.
- **시각의 JSON 모양을 맞췄다.** DB는 마이크로초까지 저장하지만 API 규칙의 시각은 초 단위라, 응답에서는 초 아래를 버린다.
- **검증 메시지를 한국어로 고정했다.** `fields[].message`가 요청의 `Accept-Language`에 따라 영어로 바뀌지 않게 했다.

### 남은 일

- Tomcat이 서블릿에 넘기기 전에 거절하는 요청(주소에 `%00` 같은 글자)은 본문 없이 `400`이 나간다. 스프링 밖이라 이 틀을 씌울 수 없다.
- `401`, `403`을 실제로 내는 일은 02 로그인(Spring Security)에서 이 틀에 맞춘다.

### 검토 순서

1. `docs/api-convention.md`, `AGENTS.md`의 "백엔드 코드": 규칙
2. `ApiResult` → `ApiError` → `ErrorCode` → `CommonErrorCode` → `BusinessException`: 틀과 코드
3. `GlobalExceptionHandler` → `ApiErrorController`: 예외를 응답으로 바꾸는 곳
4. `JacksonConfig`, `application.yml`: 설정
5. `ErrorSampleController`(테스트 전용) → `GlobalExceptionHandlerTests`: 증명
