ssafy인들을 위한 관심사 기반 커뮤니티 플랫폼

## 로컬 개발 환경

로컬에서 쓰는 MySQL과 Redis를 Docker Compose로 띄운다.

### 준비

- Docker Desktop(Docker Compose v2 포함)

### 처음 한 번

```bash
cp .env.example .env
```

내 PC에 MySQL이나 Redis가 이미 떠 있어 포트가 겹치면 `.env`의 `MYSQL_PORT`, `REDIS_PORT`를 다른 번호(예: `3307`)로 바꾼다.

### 실행과 종료

```bash
docker compose up -d     # 실행
docker compose ps        # 상태 확인 (STATUS가 healthy면 준비됨)
docker compose down      # 종료 (데이터는 남음)
docker compose down -v   # 종료하고 데이터까지 지움
```

### 접속 정보

| 항목 | 값 |
|---|---|
| MySQL | `localhost:${MYSQL_PORT}`, DB `${MYSQL_DATABASE}`, 사용자 `${MYSQL_USER}` |
| Redis | `localhost:${REDIS_PORT}` |

값은 `.env`에 적은 것을 따른다.

## 백엔드

Spring Boot 4.2, Java 25, Gradle. 코드는 `backend/`에 있다.

### 준비

- JDK 25. IntelliJ의 Project Structure → SDK → Download JDK에서 받거나 [Temurin](https://adoptium.net/)을 설치한다.
- 터미널에서 실행하면 `JAVA_HOME`이 JDK 25를 가리켜야 한다.

### 실행

```bash
cd backend
./gradlew bootRun                                   # local 프로필로 실행
./gradlew bootRun --args='--spring.profiles.active=test'   # 다른 프로필로 실행
./gradlew build                                     # 빌드와 테스트
```

| 주소 | 내용 |
|---|---|
| http://localhost:8080/swagger-ui.html | API 문서(Swagger). `prod` 프로필에서는 열리지 않는다 |
| http://localhost:8080/actuator/health | 헬스체크 |

### 프로필

| 프로필 | 쓰는 곳 | 설정 파일 |
|---|---|---|
| `local` | 내 PC. 프로필을 고르지 않으면 이것으로 뜬다 | `application-local.yml` |
| `test` | 테스트 서버 | `application-test.yml` |
| `prod` | 운영 서버 | `application-prod.yml` |

모든 프로필에 같은 설정은 `application.yml`에 둔다.

## 문서

- [브랜치 전략](docs/branch-strategy.md)
- [커밋 메시지 규칙](docs/commit-convention.md)
- [서비스 명세](docs/spec/)
- [DB 설계](docs/db/)
- [API 공통 규칙](docs/api-convention.md), [API 명세](docs/api/)
