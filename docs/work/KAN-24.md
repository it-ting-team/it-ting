# KAN-24 DB 스키마 관리 및 테스트 환경 구성

- Jira: [KAN-24](https://it-ting.atlassian.net/browse/KAN-24)
- 브랜치: `chore/be/KAN-24-db-setup`

## 계획

### 목표

DB 스키마 변경을 코드(마이그레이션)로 관리하고, 실제 MySQL로 테스트할 수 있게 한다.

### 범위

- MySQL `utf8mb4` 문자셋과 UTC 시간대
- Flyway 도입, 운영에서 `ddl-auto` 쓰지 않기
- `created_at`, `updated_at` 공통 처리
- JUnit과 Testcontainers 테스트 환경

범위 밖: 소프트 삭제 정책(도메인 모델과 탈퇴 정책을 정한 뒤).

### 정한 것 (2026-11-22)

| # | 질문 | 정한 답 | 까닭 |
|---|---|---|---|
| 1 | DB를 무엇으로 다루나 | Spring Data JPA | TypeORM과 비슷하고 Spring에서 가장 흔함 |
| 2 | 테이블을 언제 만드나 | 이번에는 기반만. 테이블은 기능 PR에서 영역별로 | `docs/db/`에 "미정"인 칸이 28곳. 그 기능을 만들 때 명세와 함께 정한다 |
| 3 | `created_at`, `updated_at`을 누가 채우나 | 엔티티 공통 부모(JPA Auditing) | 저장 직후 엔티티에 값이 있고, 테스트에서 시계를 바꿀 수 있음 |
| 4 | 시각을 어떤 타입으로 다루나 | `Instant` | 서버 시간대와 상관없이 UTC. API 규칙의 `...Z` 형식과 맞음 |
| 5 | 로컬 서버가 DB에 어떻게 붙나 | `docker compose up -d` 후 `.env` 값으로 연결 | IDE와 에이전트 어디서 띄워도 같은 흐름 |

제안서: Claude 대화에서 만든 페이지(팀 공유 안 됨). 이 문서가 기준이다.

### 바꿀 파일

- `backend/build.gradle`: JPA, Flyway, MySQL 드라이버, Testcontainers
- `backend/src/main/resources/db/migration/V1__init.sql`
- `backend/src/main/resources/application*.yml`
- `JpaConfig`, `BaseTimeEntity`, `BaseCreatedTimeEntity`
- 테스트: Testcontainers 설정, 마이그레이션 · 날짜 칼럼 검증
- `compose.yaml`, `README.md`, `AGENTS.md`

### 완료 확인 방법

- 빈 DB에 Flyway 마이그레이션이 적용된다(테스트).
- Testcontainers를 쓴 테스트가 통과한다(`./gradlew build`).
- 로컬 compose MySQL로 서버가 뜨고 헬스체크의 `db`가 `UP`이다.

## 결과

### 바꾼 것

| 파일 | 내용 |
|---|---|
| `backend/build.gradle` | `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `flyway-mysql`, `mysql-connector-j`, `spring-boot-testcontainers`, `testcontainers-mysql` |
| `db/migration/V1__init.sql` | 테이블 없이 DB 기본 문자셋을 `utf8mb4_0900_ai_ci`로 맞춤 |
| `application.yml` | 모든 프로필 `ddl-auto: validate`, `open-in-view: false`, Hibernate 시간대 UTC |
| `application-local.yml` | 저장소 루트 `.env`를 읽어 MySQL에 연결. 사용자와 비밀번호는 기본값 없음 |
| `application-test.yml`, `application-prod.yml` | DB 접속은 환경 변수 `SPRING_DATASOURCE_*`로 받는다는 주석 |
| `global/config/JpaConfig.java` | JPA Auditing, `Clock` 빈, 시각을 마이크로초로 자름 |
| `global/entity/BaseCreatedTimeEntity.java` | `created_at`만 있는 엔티티의 부모 |
| `global/entity/BaseTimeEntity.java` | `created_at`, `updated_at`이 있는 엔티티의 부모 |
| `compose.yaml` | MySQL을 utf8mb4, UTC로 실행 |
| 테스트 | `TestcontainersConfiguration`(mysql:8.4, compose와 같은 옵션), `MutableClock`, 테스트 전용 `AuditSample` 엔티티와 `V1000__create_audit_sample.sql`, `DatabaseSetupTests`, `BaseTimeEntityTests` |
| `AGENTS.md` | DB 규칙(마이그레이션으로만 테이블 만들기, 병합된 마이그레이션 수정 금지, `Instant` · `DATETIME(6)`, 부모 엔티티), 완료 기준에 Docker · Testcontainers, 리뷰 P1 2개 |
| `README.md` | 백엔드 준비에 Docker, "DB 마이그레이션" 절 |
| `docs/db/README.md` | 시각 칼럼을 `DATETIME(6)`, UTC로 |

### 확인한 것

- `./gradlew build` 통과. 테스트 12개(기존 4 + 새 8).
  - 빈 DB에 마이그레이션이 모두 적용된다.
  - DB 기본 문자셋이 `utf8mb4`, 시간대가 UTC다.
  - 한글과 이모지(`디굴디굴 🐹`)를 그대로 저장한다.
  - 저장하면 두 날짜 칼럼이 채워지고, 바꾸면 `updated_at`만 바뀐다.
  - 한국 시간대 PC에서도 DB에는 UTC 시각이 저장된다.
- `.env`가 없는 곳(백엔드만 복사한 폴더)에서도 테스트가 통과한다. CI와 Codex 클라우드를 대비한 확인.
- 로컬 compose MySQL(`.env`의 3307 포트)로 서버가 떠서 Flyway V1을 적용하고, 헬스체크의 `db`가 `UP`이다.
- 로컬 리뷰: 로컬 DB 비밀번호의 기본값을 빼고 `.env`에서만 읽게 고쳤다.

### 계획과 달라진 점

- **시각 칼럼을 `DATETIME`에서 `DATETIME(6)`으로 바꿨다(Claude 판단).** `DATETIME`은 초 단위로 잘려 같은 초 안의 순서(채팅 메시지 등)가 사라진다. 코드도 마이크로초로 잘라 저장 전후 값이 같게 했다.
- **Hibernate의 UTC 설정은 필수가 아니었다.** 설정을 빼고 테스트해도 통과했다. `Instant`만으로 UTC 저장이 보장된다. `Instant`가 아닌 시각 타입을 쓸 때를 대비해 남겨 두고 주석을 그렇게 적었다.
- **테스트 전용 테이블을 더했다.** 공통 날짜 칼럼을 검증하려면 테이블이 있어야 해서 `src/test/resources/db/migration`에 1000번으로 두었다. 운영 DB에는 만들어지지 않는다.

### 남은 일

- 운영 DB 사용자에게 `ALTER` 권한이 있어야 `V1`이 적용된다. 운영 DB를 만들 때 확인한다.
- 테스트에 Docker가 필요하다. CI에서 어떻게 돌릴지는 KAN-26에서 정한다.
- 공통 클래스의 패키지(`com.itting.global.entity`)는 KAN-4 코드 규칙에서 옮길 수 있다.
- 로컬 MySQL은 다음 `docker compose up -d` 때 새 옵션으로 다시 만들어진다(데이터는 볼륨에 남음).

### 검토 순서

1. `backend/build.gradle`: 무엇이 들어왔나
2. `V1__init.sql`, `compose.yaml`: DB가 어떻게 만들어지나
3. `application.yml`, `application-local.yml`: 서버가 DB에 어떻게 붙나
4. `JpaConfig.java` → `BaseCreatedTimeEntity.java` → `BaseTimeEntity.java`: 날짜 자동 처리
5. `TestcontainersConfiguration.java` → `AuditSample.java`와 `V1000__create_audit_sample.sql` → `DatabaseSetupTests.java` → `BaseTimeEntityTests.java`: 증명
6. `AGENTS.md`의 DB 절, `README.md`, `docs/db/README.md`: 규칙과 문서
