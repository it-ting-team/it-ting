#!/usr/bin/env bash
# 새 서버(Debian 12)를 배포받을 수 있게 준비한다. 처음 한 번 실행하고, 다시 실행해도 안전하다.
# 이 파일이 든 deploy/ 폴더를 통째로 서버에 올린 뒤 실행한다(docs/infra/test-server.md의 "서버 준비").
#
# 사용: sudo bash setup-server.sh <배포용 공개 키 파일> <TEST_HOST> <TEST_MAIL_HOST>
#   예: sudo bash deploy/setup-server.sh it-ting-deploy.pub test.example.com mail-test.example.com
#
# 하는 일
#   1. Docker 설치(없을 때만)
#   2. 배포 전용 사용자 deploy: docker 그룹에 넣고 공개 키를 등록한다
#   3. 배포 폴더 /opt/it-ting 에 이 폴더의 설정을 복사한다
#   4. /opt/it-ting/test/.env 를 만든다(없을 때만). 주소를 채우고 DB · 메일 화면의 비밀번호를 서버에서 무작위로 만든다.
#      만든 값은 화면에 찍지 않는다. Mattermost 웹후크 같은 나머지 값은 사람이 넣는다
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "sudo로 실행하세요." >&2
  exit 1
fi
if [ "$#" -ne 3 ]; then
  echo "사용: sudo bash setup-server.sh <배포용 공개 키 파일> <TEST_HOST> <TEST_MAIL_HOST>" >&2
  exit 1
fi
key_file="$1"
test_host="$2"
test_mail_host="$3"
src_dir="$(cd "$(dirname "$0")" && pwd)"

if [ ! -f "$key_file" ] || ! grep -qE '^(ssh-ed25519|ssh-rsa|ecdsa-sha2-[a-z0-9-]+) ' "$key_file"; then
  echo "공개 키 파일(.pub)이 아닙니다: $key_file" >&2
  exit 1
fi
for host in "$test_host" "$test_mail_host"; do
  if ! printf '%s' "$host" | grep -qE '^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$'; then
    echo "주소는 https:// 없이 이름만 적습니다: $host" >&2
    exit 1
  fi
done

echo "== 1. Docker"
if ! command -v docker > /dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi
docker --version
docker compose version

echo "== 2. 배포 사용자 deploy"
id deploy > /dev/null 2>&1 || useradd --create-home --shell /bin/bash deploy
usermod -aG docker deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
# 등록된 키를 이 공개 키 하나로 맞춘다.
install -m 600 -o deploy -g deploy "$key_file" /home/deploy/.ssh/authorized_keys

echo "== 3. 배포 폴더 /opt/it-ting"
install -d -o deploy -g deploy /opt/it-ting
# 서버의 .env는 건드리지 않는다. 목 사이트용 폴더는 서버에 필요 없다.
tar -C "$src_dir" --exclude=./mock --exclude='.env' -cf - . | tar -C /opt/it-ting -xf -
chown -R deploy:deploy /opt/it-ting

echo "== 4. /opt/it-ting/test/.env"
env_file=/opt/it-ting/test/.env
if [ -f "$env_file" ]; then
  echo "이미 있어 그대로 둡니다."
else
  random_hex() { head -c "$1" /dev/urandom | od -An -tx1 | tr -d ' \n'; }
  sed \
    -e "s|^TEST_HOST=.*|TEST_HOST=${test_host}|" \
    -e "s|^TEST_MAIL_HOST=.*|TEST_MAIL_HOST=${test_mail_host}|" \
    -e "s|^MYSQL_ROOT_PASSWORD=.*|MYSQL_ROOT_PASSWORD=$(random_hex 24)|" \
    -e "s|^MYSQL_PASSWORD=.*|MYSQL_PASSWORD=$(random_hex 24)|" \
    -e "s|^MAILPIT_UI_AUTH=.*|MAILPIT_UI_AUTH=team:$(random_hex 8)|" \
    /opt/it-ting/test/.env.example > "$env_file"
  chown deploy:deploy "$env_file"
  chmod 600 "$env_file"
  echo "만들었습니다. 주소와 DB · 메일 화면의 비밀번호를 채웠습니다(값은 여기에 찍지 않습니다)."
fi
echo "아직 빈 값:"
grep -E '^[A-Z0-9_]+=$' "$env_file" | cut -d= -f1 | sed 's/^/  /' || true

echo "== 디스크와 메모리"
df -h / | tail -1
grep -E '^(MemTotal|SwapTotal):' /proc/meminfo
echo "== 끝"
