# KAN-37 공통 API: 캠퍼스 · 층 목록과 기수 목록

- Jira: [KAN-37](https://it-ting.atlassian.net/browse/KAN-37)
- 브랜치: `feature/be/KAN-37-campus-generation`

## 계획

### 목표

로그인 없이 부르는 공통 목록 API 두 개를 만든다. 회원가입 3과 추천 친구 설정이 쓴다.

- `GET /api/v1/campuses` ([API 명세](../api/00-common.md#get-apiv1campuses))
- `GET /api/v1/generations` ([API 명세](../api/00-common.md#get-apiv1generations))

### 정한 것 (2026-11-22)

| # | 질문 | 정한 답 | 정한 사람 |
|---|---|---|---|
| 1 | 캠퍼스 · 층을 어떻게 넣나 | 명세의 임시 값(서울 · 대전 · 부울경 · 구미 · 광주, 캠퍼스마다 1 ~ 20층)을 마이그레이션으로 넣는다. 실제 값이 정해지면 새 마이그레이션으로 고친다 | 사용자 |
| 2 | 기수 목록 | 15 · 16 (서버 설정 `itting.generations`) | 사용자 |
| 3 | `campus`, `campus_floor`의 미정 칸 | `name` `VARCHAR(20)` NOT NULL UNIQUE, `sort_order` `INT` NOT NULL, `floor` `INT` NOT NULL | Claude |

## 결과

### 바꾼 것

| 파일 | 내용 |
|---|---|
| `db/migration/V2__create_campus.sql` | `campus`, `campus_floor` 테이블(FK, `UNIQUE(campus_id, floor)`)과 임시 값 |
| `campus/` | `Campus`, `CampusFloor` 엔티티, 저장소, `CampusService`, `CampusController`, `CampusListResponse` |
| `generation/` | `GenerationProperties`(서버 설정), `GenerationService`, `GenerationController`, `GenerationListResponse` |
| `application.yml` | `itting.generations: 15, 16` |
| `docs/db/burrow.md` | `campus`, `campus_floor`의 미정 칸 세 개, 임시 값을 넣는 마이그레이션 |
| `docs/api/00-common.md` | 캠퍼스 순서가 같을 때 id 순, 기수 설정 이름, 구현 단계로 넘긴 것 갱신 |

### 확인한 것

- `./gradlew build` 통과. 테스트 34개(기존 27 + 새 7).
  - `CampusApiTests`: 임시 값 다섯 캠퍼스가 순서대로, 층은 1 ~ 20층 순서대로 나온다. 지운 캠퍼스와 층은 빠지고, 층이 없는 캠퍼스는 `floors: []`다. `POST`는 `405`.
  - `GenerationApiTests`: `[16, 15]`. `POST`는 `405`.
  - `GenerationServiceTests`: 설정 순서와 상관없이 큰 기수부터, 겹치면 한 번만, 설정이 없으면 빈 목록.

### 계획과 달라진 점

- 캠퍼스의 `sort_order`가 같을 때의 순서가 명세에 없어 id 순으로 정하고 API 명세에 적었다.
- 층 엔티티는 캠퍼스를 객체로 잇지 않고 `campusId`만 가진다. 목록을 두 번의 조회로 읽어 묶기만 하므로 연관 관계가 필요 없다.

### 검토 순서

1. `V2__create_campus.sql`, `docs/db/burrow.md`
2. `CampusController` → `CampusService` → 저장소 · 엔티티
3. `GenerationController` → `GenerationService` → `GenerationProperties`, `application.yml`
4. `CampusApiTests`, `GenerationApiTests`, `GenerationServiceTests`
