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
3. 같은 수정을 `test`와 `dev`에도 반영한다.

## 저장소 구조

프론트엔드와 백엔드를 한 저장소에서 관리하는 모노레포를 사용한다.

```text
frontend/  프론트엔드
backend/   백엔드
docs/      프로젝트 공통 문서
```
