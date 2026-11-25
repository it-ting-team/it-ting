#!/usr/bin/env bash
# 서버에서 테스트 서버의 이미지를 받아 다시 띄운다. GitHub Actions(deploy-test.yml)가 SSH로 부른다.
# 사용: IMAGE_TAG=<커밋 SHA> bash /opt/it-ting/deploy.sh
#   IMAGE_TAG  받을 이미지의 태그. 없으면 test(가장 최근에 올린 것)
#   SKIP_PULL  1이면 이미지를 받지 않는다(내 PC에서 만든 이미지로 확인할 때)
set -euo pipefail
cd "$(dirname "$0")"

export IMAGE_TAG="${IMAGE_TAG:-test}"

if [ ! -f test/.env ]; then
  echo "test/.env가 없습니다. test/.env.example을 복사해 채우세요." >&2
  exit 1
fi

# 프록시와 테스트 서버가 함께 쓰는 네트워크. 없을 때만 만든다.
docker network inspect it-ting-edge > /dev/null 2>&1 || docker network create it-ting-edge > /dev/null

test_compose=(docker compose --project-directory test -f test/compose.yaml)
edge_compose=(docker compose --project-directory edge -f edge/compose.yaml --env-file test/.env)

echo "== 테스트 서버 (이미지 태그: ${IMAGE_TAG})"
if [ "${SKIP_PULL:-0}" != "1" ]; then
  "${test_compose[@]}" pull
fi
# 백엔드 헬스체크가 통과할 때까지 기다린다. 실패하면 여기서 멈춘다.
"${test_compose[@]}" up -d --remove-orphans --wait --wait-timeout 300

echo "== 공용 프록시"
"${edge_compose[@]}" up -d --wait --wait-timeout 60
# Caddyfile이 바뀌었으면 끊김 없이 다시 읽는다.
"${edge_compose[@]}" exec -T caddy caddy reload --config /etc/caddy/Caddyfile

# 쓰지 않는 옛 이미지를 지운다. 디스크가 차지 않게 한다.
docker image prune -f > /dev/null
echo "== 끝"
