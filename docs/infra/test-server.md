# 테스트 서버와 목 사이트

배포는 둘이다. 실제 데이터로 도는 테스트 서버(GCP)와 목 데이터로 도는 목 사이트(Vercel).

| | 테스트 서버 (GCP) | 목 사이트 (Vercel) |
|---|---|---|
| 주소 | https://test.diguldigul.site | https://diguldigul-mock.vercel.app |
| 올라가는 것 | 프론트엔드, 백엔드, MySQL, Redis, Mailpit | 프론트엔드만 |
| 데이터 | 실제 DB. 프론트엔드가 모든 영역을 실제 API로 부른다 | 브라우저 안의 목 데이터 |
| 다시 배포되는 때 | `dev` → `test` PR을 병합할 때 | `dev`에 `frontend/` 변경이 병합될 때 |
| 워크플로 | `.github/workflows/deploy-test.yml` | `.github/workflows/deploy-mock.yml` |
| 쓰는 곳 | 실제 가입 · 로그인, 프론트엔드와 백엔드 연동 확인 | 화면과 디자인 확인, 아직 API가 없는 화면 보기 |

테스트 서버에서는 아직 만들지 않은 API의 화면(대화방 등)이 오류로 보인다. 그 화면은 목 사이트에서 본다.

## 테스트 서버의 구성

VM 한 대(GCP Compute Engine `e2-medium`, 서울)에 Docker Compose 묶음 둘을 띄운다.

| 묶음 | 폴더 | 들어 있는 것 |
|---|---|---|
| 공용 프록시 | `deploy/edge/` | Caddy. HTTPS 인증서를 자동으로 받고, 서버에서 80 · 443만 밖으로 연다. 나중에 운영 서버도 여기에 붙인다 |
| 테스트 서버 | `deploy/test/` | 프론트엔드(Next.js), 백엔드(Spring Boot, 프로필 `test`), MySQL 8.4, Redis 7.4, Mailpit. 포트를 밖으로 열지 않는다 |

요청이 가는 길:

| 주소 | 가는 곳 |
|---|---|
| `https://<TEST_HOST>/api/*`, `/images/*`, `/swagger-ui.html`, `/swagger-ui/*`, `/v3/api-docs*`, `/actuator/health` | 백엔드 |
| `https://<TEST_HOST>/` 의 나머지 | 프론트엔드 |
| `https://<TEST_MAIL_HOST>/` | Mailpit(서버가 보낸 메일을 받아 둔 화면). 아이디와 비밀번호를 묻는다 |

- 화면과 API가 같은 주소라 CORS 설정 없이 세션 쿠키가 실린다.
- 올린 사진은 백엔드가 서버의 볼륨(`images-data`)에 두고 `https://<TEST_HOST>/images/...`로 준다. 컨테이너를 다시 띄워도 남는다.
- 메일은 실제로 보내지 않는다. 인증 메일과 재설정 메일은 Mailpit 화면에서 본다.
- Swagger는 `https://<TEST_HOST>/swagger-ui.html`에서 연다.

### 저장소의 파일

| 파일 | 내용 |
|---|---|
| `backend/Dockerfile`, `frontend/Dockerfile` | 이미지 둘. 올라가는 곳은 `ghcr.io/it-ting-team/it-ting-backend`, `…/it-ting-frontend` |
| `deploy/edge/compose.yaml`, `deploy/edge/caddy/Caddyfile` | 공용 프록시 |
| `deploy/test/compose.yaml` | 테스트 서버 |
| `deploy/test/.env.example` | 서버의 `.env`에 넣을 이름 목록(값 없음) |
| `deploy/test/frontend.build.env` | 테스트 서버용 프론트엔드 빌드 값(비밀값 아님) |
| `deploy/mock/frontend.build.env` | 목 사이트용 프론트엔드 빌드 값(비밀값 아님) |
| `deploy/deploy.sh` | 서버에서 이미지를 받아 다시 띄우는 스크립트 |
| `deploy/setup-server.sh` | 새 서버를 처음 준비하는 스크립트(Docker, 배포 사용자, 배포 폴더, `.env`) |
| `deploy/set-env.sh` | 서버의 `.env`에 값을 넣는 스크립트. 값을 화면에 찍지 않는다 |

서버에는 `deploy/` 폴더가 `/opt/it-ting/`으로 복사된다. 서버에만 있는 파일은 `/opt/it-ting/test/.env` 하나다.

### 지킬 것

- MUST NOT: 비밀값(DB 비밀번호, 웹후크 URL, SSH 키, Vercel 토큰)을 저장소의 파일에 적지 않는다. 서버의 `.env`와 GitHub 시크릿에만 둔다.
- MUST NOT: `deploy/*/frontend.build.env`에 비밀값을 넣지 않는다. 빌드할 때 JS에 박혀 누구나 볼 수 있다.
- MUST: 서버 설정을 바꿀 때는 서버에서 파일을 직접 고치지 않고 `deploy/`를 고쳐 PR로 올린다. 다음 배포가 서버의 파일을 덮어쓴다(`.env`만 빼고).

## 배포

### 테스트 서버에 배포하기

1. `dev` → `test` PR을 만들어 병합한다(`docs/branch-strategy.md`).
2. `deploy-test` 워크플로가 돈다: 백엔드 `./gradlew build`(테스트 전체), 프론트엔드 타입 검사 · 단위 테스트 → 이미지 둘을 GHCR에 올림(태그는 커밋 SHA와 `test`) → 서버가 받아 다시 띄움 → `https://<TEST_HOST>/actuator/health`와 첫 화면 확인.
3. 검사가 실패하면 이미지를 만들지 않고 서버는 그대로다.

같은 커밋을 다시 배포하려면 GitHub → Actions → `deploy-test` → Run workflow에서 브랜치 `test`를 고르고 `image_tag`를 비워 실행한다.

### 배포 결과 알림

두 배포 워크플로는 끝나면 성공 · 실패, 주소, 커밋, 실행 주소를 Discord의 배포 채널로 보낸다. 웹후크는 시크릿 `DISCORD_WEBHOOK_DEPLOY_URL`에 있다. PR 알림 채널(`DISCORD_WEBHOOK_PR_URL`)과는 다른 채널이다. 시크릿이 없으면 알림만 건너뛰고 배포는 그대로 된다. 취소된 실행은 알리지 않는다.

### 되돌리기

1. 되돌아갈 커밋의 SHA(40자)를 찾는다. `test` 브랜치의 이전 커밋이다: `git log origin/test --format=%H -5`
2. Actions → `deploy-test` → Run workflow → 브랜치 `test`, `image_tag`에 그 SHA를 적어 실행한다.
3. 새로 빌드하지 않고 그 SHA의 이미지를 받아 띄운다. DB는 되돌아가지 않는다. 이미 적용된 마이그레이션은 그대로 남는다.

### 목 사이트에 배포하기

`dev`에 `frontend/`나 `deploy/mock/`의 변경이 병합되면 `deploy-mock` 워크플로가 Vercel에 올린다. 직접 돌리려면 Actions → `deploy-mock` → Run workflow에서 브랜치 `dev`를 고른다.

## 운영 (서버에서)

서버에 들어가기: `gcloud compute ssh it-ting-test --project=it-ting-server --zone=asia-northeast3-a`

아래 명령은 서버에서 실행한다. `docker` 명령에 권한 오류가 나면 앞에 `sudo`를 붙인다.

| 하려는 일 | 명령 |
|---|---|
| 상태 보기 | `docker compose -f /opt/it-ting/test/compose.yaml ps` |
| 백엔드 로그 | `docker compose -f /opt/it-ting/test/compose.yaml logs -f --tail 200 backend` |
| 프론트엔드 로그 | `docker compose -f /opt/it-ting/test/compose.yaml logs -f --tail 200 frontend` |
| 프록시 로그(인증서 발급 실패 등) | `docker compose -f /opt/it-ting/edge/compose.yaml --env-file /opt/it-ting/test/.env logs --tail 200 caddy` |
| 백엔드만 다시 시작 | `docker compose -f /opt/it-ting/test/compose.yaml restart backend` |
| `.env`를 고친 뒤 반영 | `bash /opt/it-ting/deploy.sh` |
| DB에 들어가기 | `docker compose -f /opt/it-ting/test/compose.yaml exec mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'` |
| 메모리 보기 | `docker stats --no-stream` |
| 디스크 보기 | `df -h /` , `docker system df` |

- DB는 백업하지 않는다. 테스트 데이터라 잃어도 되는 것으로 정했다.
- DB를 처음 상태로 되돌리려면 `docker compose -f /opt/it-ting/test/compose.yaml down -v` 후 `bash /opt/it-ting/deploy.sh`를 실행한다. 데이터, 로그인 세션, 올린 사진이 모두 지워진다.
- 백엔드에 새 필수 환경 변수가 생기면(없으면 서버가 뜨지 않는 것) 그 PR에서 `deploy/test/compose.yaml`의 `backend.environment`에도 더한다. 비밀값이면 `deploy/test/.env.example`에 이름을 더하고 서버의 `.env`에 값을 넣는다. 필요한 이름은 `application-test.yml` 맨 위에 적혀 있다.
- 컨테이너마다 메모리 상한이 있다(`deploy/test/compose.yaml`의 `mem_limit`). 합은 약 3.4GB다. 로컬에서 띄운 직후에는 모두 합쳐 약 1GB를 썼다(2026-11-25).

### 프론트엔드 빌드 값을 바꿀 때

`deploy/test/frontend.build.env`를 고쳐 PR로 올린다. 이름의 뜻은 `frontend/.env.example`에 있다.

- 실시간 소식 API(KAN-57)가 병합되면 `NEXT_PUBLIC_API_MOCK_AREAS=events`의 값을 비운다. 지금은 SSE API가 없어 연결만 꺼 두었다.
- 값은 이미지를 만들 때 박힌다. 서버에서 바꿀 수 없고 다시 배포해야 한다.

### 내 PC의 프론트엔드에서 테스트 서버 부르기

`frontend/.env.local`에 아래를 적고 `npm run dev`를 실행한다. Next가 `/api/v1/*`을 테스트 서버로 넘긴다.

```bash
NEXT_PUBLIC_API_MODE=real
NEXT_PUBLIC_API_MOCK_AREAS=events
API_PROXY_TARGET=https://<TEST_HOST>
```

테스트 서버는 이 경우를 위해 출처 `http://localhost:3000`의 요청을 받는다(`deploy/test/compose.yaml`의 `ITTING_CORS_ALLOWED_ORIGINS`).

## 처음 한 번: 테스트 서버 만들기

계정, 결제, 비밀값이 걸린 일이라 사람이 한다. 명령은 Git Bash 기준이고 한 줄씩 실행한다. `<…>`는 자기 값으로 바꾼다.

### 1. GCP 프로젝트와 VM

프로젝트 ID `it-ting-server`가 이미 쓰이고 있으면 `it-ting-server-2`처럼 바꾸고, 아래 명령의 `it-ting-server`를 모두 그 값으로 바꾼다.

```bash
# 프로젝트를 만들고 결제 계정을 잇는다
gcloud projects create it-ting-server --name="it-ting"
gcloud billing accounts list
gcloud billing projects link it-ting-server --billing-account=<결제 계정 ID>
gcloud services enable compute.googleapis.com --project=it-ting-server

# 고정 IP. 두 번째 명령이 주소를 보여 준다. DNS에 적을 값이다
gcloud compute addresses create it-ting-ip --project=it-ting-server --region=asia-northeast3
gcloud compute addresses describe it-ting-ip --project=it-ting-server --region=asia-northeast3 --format="value(address)"

# 방화벽: 웹(80, 443)을 연다. SSH(22)는 새 프로젝트의 기본 규칙 default-allow-ssh가 이미 열어 둔다
gcloud compute firewall-rules create it-ting-allow-web --project=it-ting-server --network=default --direction=INGRESS --allow=tcp:80,tcp:443 --target-tags=it-ting-web --source-ranges=0.0.0.0/0
gcloud compute firewall-rules list --project=it-ting-server
# 쓰지 않는 원격 데스크톱(3389) 규칙은 지운다
gcloud compute firewall-rules delete default-allow-rdp --project=it-ting-server --quiet

# VM. 만들기 전에 GCP 가격 계산기로 한 달 금액을 확인한다
gcloud compute instances create it-ting-test --project=it-ting-server --zone=asia-northeast3-a --machine-type=e2-medium --image-family=debian-12 --image-project=debian-cloud --boot-disk-size=50GB --boot-disk-type=pd-balanced --address=it-ting-ip --tags=it-ting-web

# 예산 알림. 금액은 한 달 예상(약 5 ~ 6만 원)보다 조금 높게 잡았다. 바꿔도 된다
gcloud services enable billingbudgets.googleapis.com --project=it-ting-server
gcloud billing budgets create --billing-account=<결제 계정 ID> --display-name="it-ting-server" --budget-amount=70000KRW --filter-projects=projects/it-ting-server --threshold-rule=percent=0.5 --threshold-rule=percent=0.9 --threshold-rule=percent=1.0
```

`debian-12` 이미지가 없다고 나오면 `gcloud compute images list --filter="family~debian"`으로 지금 있는 이름을 본다.

### 2. DNS

도메인을 산 곳의 DNS 설정에서 A 레코드 둘을 위 고정 IP로 건다.

| 이름 | 종류 | 값 |
|---|---|---|
| `test` | A | 고정 IP |
| `mail-test` | A | 고정 IP |

확인: `nslookup test.<도메인>`이 고정 IP를 답한다. 인증서는 DNS가 이 서버를 가리킨 뒤에야 발급된다.

### 3. 서버 준비

내 PC에서 배포용 SSH 키를 만든다. 암호는 비워 둔다. GitHub Actions가 암호 없이 써야 한다.

```bash
ssh-keygen -t ed25519 -C deploy -f "$HOME/.ssh/it-ting-deploy" -N ""
```

저장소 폴더에서 `deploy/` 폴더와 공개 키(`.pub`)를 서버에 올리고 준비 스크립트를 실행한다. 개인 키는 올리지 않는다. 올리는 곳은 `it-ting-test:.`(홈 폴더)로 적는다. Windows의 gcloud가 쓰는 `pscp`는 `~/`를 알아듣지 못한다.

```bash
gcloud compute scp --recurse deploy "$HOME/.ssh/it-ting-deploy.pub" it-ting-test:. --project=it-ting-server --zone=asia-northeast3-a --strict-host-key-checking=no --quiet
gcloud compute ssh it-ting-test --project=it-ting-server --zone=asia-northeast3-a --strict-host-key-checking=no --quiet --command="sudo bash deploy/setup-server.sh it-ting-deploy.pub test.<도메인> mail-test.<도메인>"
```

`deploy/setup-server.sh`가 하는 일:

1. Docker를 깐다(없을 때만).
2. 배포 전용 사용자 `deploy`를 만들어 `docker` 그룹에 넣고 위 공개 키를 등록한다.
3. `/opt/it-ting`에 배포 설정을 복사한다.
4. `/opt/it-ting/test/.env`를 만든다(없을 때만). 주소 둘을 채우고, DB 비밀번호 둘과 메일 화면의 비밀번호를 서버에서 무작위로 만든다. 만든 값은 화면에 찍지 않는다.

다시 실행해도 된다. 이미 있는 `.env`는 건드리지 않는다. `docker` 그룹의 사용자는 서버에서 root와 같은 일을 할 수 있으므로 `deploy`의 개인 키는 GitHub 시크릿에만 둔다.

### 4. 서버의 `.env`에 남은 값 넣기

스크립트가 끝에 "아직 빈 값"을 보여 준다. 값은 비밀값이라 채팅이나 문서에 붙여 넣지 않는다. 넣는 방법은 둘이다.

내 PC의 `.env`에 같은 이름으로 적어 둔 값을 서버로 보낸다. `deploy/set-env.sh`가 받아 서버의 `.env`에 넣고 이름만 화면에 찍는다. 저장소 폴더에서 실행한다.

```bash
grep -E '^(MATTERMOST_WEBHOOK_URLS_(15|16)|ITTING_GOOGLE_CLIENT_(ID|SECRET))=' .env | ssh -i ~/.ssh/it-ting-deploy deploy@test.<도메인> "bash /opt/it-ting/set-env.sh"
```

- `test/.env.example`에 있는 이름만 받는다. 주소와 DB 값(`TEST_HOST`, `MYSQL_*`), 빈 값은 받지 않는다.
- 보낼 때마다 그 이름의 값 전체가 바뀐다. 웹후크처럼 여러 개를 쉼표로 잇는 값은 늘 전체를 보낸다.
- 서버가 이미 떠 있으면 `ssh … "bash /opt/it-ting/deploy.sh"`를 실행해야 반영된다.

또는 서버에 들어가 직접 고친다.

```bash
gcloud compute ssh it-ting-test --project=it-ting-server --zone=asia-northeast3-a
sudo -u deploy nano /opt/it-ting/test/.env
```

| 이름 | 넣을 값 |
|---|---|
| `TEST_HOST`, `TEST_MAIL_HOST` | 스크립트가 채웠다. `test.<도메인>`, `mail-test.<도메인>` |
| `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD` | 스크립트가 무작위로 만들었다 |
| `MAILPIT_UI_AUTH` | 스크립트가 `team:<무작위>`로 만들었다. 받은 메일 화면에 들어갈 때 이 파일에서 읽는다 |
| `MATTERMOST_WEBHOOK_URLS_15`, `_16` | 그 기수 Mattermost 서버의 웹후크. 여러 사람의 것을 쉼표로 이으면 돌아가며 쓰고, 실패하면 다음 것으로 보낸다. 비우면 그 기수는 SSAFY 인증이 `503`이라 가입을 끝낼 수 없다 |
| `ITTING_PUSH_*`, `ITTING_GOOGLE_*` | 선택. 비우면 서버는 뜨고 그 기능만 실패한다 |

Google 로그인을 켜려면 Google Cloud 콘솔(프로젝트 `it-ting-server`)에서 "웹 애플리케이션" OAuth 클라이언트를 만들고 "승인된 리디렉션 URI"에 `https://<TEST_HOST>/api/v1/oauth/google/callback`을 넣는다. 동의 화면의 게시 상태가 "테스트"인 동안에는 테스트 사용자로 등록한 Google 계정만 로그인할 수 있다. 푸시 키는 `backend/`에서 `./gradlew -q vapidKeys`로 만든다(`README.md`의 "브라우저 푸시 키").

### 5. GitHub 시크릿과 변수

내 PC의 저장소 폴더에서 실행한다. 값은 화면에 남지 않는다.

```bash
gh variable set TEST_HOST --body "test.<도메인>"
gh secret set TEST_SSH_USER --body "deploy"
gh secret set TEST_SSH_KEY < ~/.ssh/it-ting-deploy
ssh-keyscan -t ed25519 test.<도메인> | gh secret set TEST_SSH_KNOWN_HOSTS
```

| 이름 | 종류 | 값 |
|---|---|---|
| `TEST_HOST` | 변수 | 테스트 서버 주소(`https://` 없이). 배포 뒤 확인과 SSH 접속에 쓴다 |
| `TEST_SSH_USER` | 시크릿 | `deploy` |
| `TEST_SSH_KEY` | 시크릿 | 배포용 개인 키(`~/.ssh/it-ting-deploy`의 내용) |
| `TEST_SSH_KNOWN_HOSTS` | 시크릿 | 서버의 호스트 키. 다른 서버로 잘못 붙지 않게 한다 |
| `DISCORD_WEBHOOK_DEPLOY_URL` | 시크릿 | 배포 결과를 받을 Discord 채널의 웹후크. 저장소 Settings → Secrets and variables → Actions에서 넣는다 |

PowerShell에서는 `<`를 쓸 수 없다. `Get-Content -Raw ~/.ssh/it-ting-deploy | gh secret set TEST_SSH_KEY`로 넣는다.

### 6. 브랜치 보호와 첫 배포

1. 규칙 세트 `protect-main-dev`의 대상에 `test`를 더한다([CI의 처음 한 번](../ci.md#처음-한-번-저장소-관리자)).
2. `dev` → `test` PR을 만들어 병합한다. `deploy-test`가 돈다.
3. 처음에는 "서버가 이미지를 받아 다시 띄우기"에서 `unauthorized`로 실패한다. GHCR에 처음 올라간 패키지가 비공개라 서버가 받지 못하는 것이다. 패키지 둘(`it-ting-backend`, `it-ting-frontend`)을 공개로 바꾼다.
   - 패키지 화면(조직 `it-ting-team` → Packages → 패키지) → Package settings → 맨 아래 Danger Zone → Change visibility → Public
   - Public을 고를 수 없으면 조직 Settings → Packages → Package creation에서 Public을 켜고 저장한 뒤 다시 한다
4. Actions의 실패한 실행에서 Re-run failed jobs를 누른다.
5. `https://<TEST_HOST>/`가 열리고 가입 · 로그인이 되는지 본다.

## 처음 한 번: 목 사이트 만들기

Vercel 계정과 토큰이 걸린 일이라 사람이 한다. 로그인과 토큰 입력은 값을 묻는 방식이라 터미널에서 직접 실행한다.

1. 내 PC의 `frontend/` 폴더에서 Vercel에 로그인하고 프로젝트를 만든다. 계정이 없으면 로그인 화면에서 무료 요금제(Hobby)로 가입한다. 프로젝트 이름이 주소(`<이름>.vercel.app`)가 된다.

   ```bash
   cd frontend
   npx vercel@66.6.0 login
   npx vercel@66.6.0 link --yes --project <프로젝트 이름>
   ```

   - "Failed to connect … to project"라고 나오는 것은 괜찮다. CLI가 저장소를 Vercel에 연결하려다 실패한 것이고, 우리는 연결하지 않는다. 올리는 일은 GitHub Actions가 한다.
   - CLI가 `frontend/.gitignore` 끝에 `.vercel`, `.env*`를 덧붙이고 `frontend/.env.local`을 만든다. `.gitignore`의 변경은 되돌린다(`git checkout -- frontend/.gitignore`). 필요한 줄은 이미 있다.
   - Vercel 화면의 프로젝트 설정에서 Root Directory는 비워 둔다. 워크플로가 `frontend/` 안에서 명령을 실행한다.
2. Vercel → Account Settings → Tokens에서 토큰을 만든다.
3. 저장소 폴더(`frontend/`의 위)에서 시크릿 셋을 넣는다. 앞의 두 줄은 방금 만들어진 `frontend/.vercel/project.json`에서 아이디를 읽어 넣는다. `frontend/` 안에서 실행하면 파일을 찾지 못해 빈 값이 들어간다.

   ```bash
   node -e "process.stdout.write(require('./frontend/.vercel/project.json').orgId)" | gh secret set VERCEL_ORG_ID
   node -e "process.stdout.write(require('./frontend/.vercel/project.json').projectId)" | gh secret set VERCEL_PROJECT_ID
   gh secret set VERCEL_TOKEN
   ```

   마지막 명령은 값을 물으면 토큰을 붙여 넣는다.
4. Actions → `deploy-mock` → Run workflow에서 브랜치 `dev`로 실행한다. 끝나면 로그와 Discord 알림에 주소가 나온다.
5. 주소가 `https://diguldigul-mock.vercel.app`와 다르면 `deploy-mock.yml`의 `MOCK_SITE_URL`을 고친다. 알림에 적히는 주소다.

목 사이트의 설정은 `deploy/mock/frontend.build.env`에 있다: 모든 영역 목, 실험실 켬(목 서버 손잡이 배지), 로그인 검사는 명세대로. 시연 계정은 `frontend/src/data/auth.ts` 맨 위에 있다.

## 비용

- VM `e2-medium`과 디스크 50GB, 고정 IP로 한 달 약 6만 원 안팎이다(어림한 값. 디스크는 2026-11-25에 50GB로 만들었다). 만들기 전에 GCP 가격 계산기로 다시 확인한다.
- 지금 쓴 금액은 GCP 콘솔 → 결제 → 보고서에서 프로젝트 `it-ting-server`로 걸러 본다.
- 한동안 쓰지 않을 때는 VM을 멈춘다. 멈춘 동안에는 디스크와 고정 IP 값만 나온다.

  ```bash
  gcloud compute instances stop it-ting-test --project=it-ting-server --zone=asia-northeast3-a
  gcloud compute instances start it-ting-test --project=it-ting-server --zone=asia-northeast3-a
  ```

- Vercel 목 사이트는 무료 요금제로 시작한다.
