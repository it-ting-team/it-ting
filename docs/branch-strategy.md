# 브랜치 전략

## 환경 브랜치

| 브랜치 | 역할 |
|---|---|
| `main` | 실제 운영 서버 배포 |
| `test` | 테스트 서버 배포 |
| `dev` | 개발 메인 브랜치 |

## 병합 흐름

```text
작업 브랜치 → dev → test → main
```

| PR의 기준 브랜치 | 받는 원본 브랜치 |
|---|---|
| `dev` | 모든 작업 브랜치 |
| `test` | `dev`만 |
| `main` | `test`, `hotfix/*`만 |

다른 조합의 PR은 CI의 병합 흐름 검사가 실패해 병합되지 않는다([CI](ci.md)).

## 보호 규칙

`main`, `test`, `dev`에는 GitHub 규칙 세트 `protect-main-dev`가 적용된다. 관리자를 포함해 예외가 없다.

- MUST: `main`, `test`, `dev`의 변경은 PR로만 병합한다.
- MUST: PR에 든 모든 커밋은 서명되어 있어야 한다. 설정은 [커밋 서명](commit-convention.md#커밋-서명)을 따른다.
- MUST: PR의 CI 검사 `ci`가 실패하면 고친 뒤 병합한다. 지금은 필수 검사가 아니라 GitHub가 막지는 않는다. 검사 내용은 [CI](ci.md)를 따른다.
- MUST NOT: `main`, `test`, `dev`에 직접 푸시하지 않는다.
- MUST NOT: `main`, `test`, `dev`에 강제 푸시하거나 브랜치를 삭제하지 않는다.

| 시도 | 결과 |
|---|---|
| `git push origin dev` | 거부 |
| `git push --force origin dev` | 거부 |
| 서명 없는 커밋이 든 PR 병합 | 차단 |
| `ci`가 실패한 PR 병합 | 허용(필수 검사로 걸기 전까지). 고친 뒤 병합한다 |
| 작업 브랜치에 푸시, 강제 푸시 | 허용 |

`test` 브랜치는 KAN-26이 병합된 뒤 규칙 세트에 더한다([CI의 처음 한 번](ci.md#처음-한-번-저장소-관리자)). 필수 검사 `ci`는 나중에 건다([CI의 나중에 할 일](ci.md#나중에-할-일-필수-검사-ci-걸기)).

규칙 세트는 저장소 Settings → Rules → Rulesets에서 확인한다. 규칙을 바꿀 때는 이 문서를 함께 고치는 PR을 올린다.

## 작업 브랜치 이름

```text
<유형>/<영역>/KAN-<번호>-<설명>
```

설명은 영어 소문자와 하이픈으로 짧게 작성한다.

### 유형

| 유형 | 용도 |
|---|---|
| `feature` | 기능 개발 |
| `fix` | 버그 수정 |
| `chore` | 설정, 환경 구성 |
| `docs` | 문서 작업 |
| `hotfix` | 운영 긴급 수정 |

### 영역

| 영역 | 대상 |
|---|---|
| `fe` | 프론트엔드 |
| `be` | 백엔드 |
| `infra` | CI/CD, 배포, 서버 |
| `common` | 문서, 저장소 공통 설정 등 특정 영역에 속하지 않는 작업 |

두 개 이상의 영역에 걸치는 작업은 `common`을 사용한다.

### 예시

```text
feature/be/KAN-23-spring-setup
chore/fe/KAN-25-nextjs-setup
chore/infra/KAN-26-ci-setup
chore/common/KAN-8-branch-setup
```

## 긴급 수정

1. `main`에서 `hotfix/<영역>/KAN-<번호>-<설명>` 브랜치를 만든다.
2. 수정 후 `main`에 병합한다.
3. 같은 브랜치로 `dev`에 PR을 올려 반영한다. `test`에는 다음 `dev` → `test` PR로 들어간다(`test`로 가는 PR은 `dev`에서만 받는다).

## 저장소 구조

프론트엔드와 백엔드를 한 저장소에서 관리하는 모노레포를 사용한다.

```text
frontend/  프론트엔드
backend/   백엔드
docs/      프로젝트 공통 문서
```
