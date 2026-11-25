#!/usr/bin/env bash
# 서버의 테스트 서버 .env에 값을 넣는다. 표준 입력으로 받은 KEY=VALUE 줄을 반영하고, 값은 화면에 찍지 않는다.
# 내 PC의 .env에 있는 값을 서버로 옮길 때 쓴다(docs/infra/test-server.md의 "서버의 .env에 남은 값 넣기").
#
# 사용(내 PC의 저장소 폴더에서):
#   grep -E '^(MATTERMOST_WEBHOOK_URLS_15|MATTERMOST_WEBHOOK_URLS_16)=' .env \
#     | ssh -i ~/.ssh/it-ting-deploy deploy@<TEST_HOST> 'bash /opt/it-ting/set-env.sh'
#
# - 이름이 test/.env.example에 있는 것만 받는다. 있는 이름이면 값을 바꾸고, 없던 이름이면 끝에 더한다.
# - 주소(TEST_HOST, TEST_MAIL_HOST)와 DB 값(MYSQL_*)은 받지 않는다. DB 비밀번호를 바꾸면 이미 만든 DB에 붙지 못한다.
# - 빈 값은 받지 않는다. 값을 지우려면 서버에서 파일을 직접 고친다.
# - 반영은 다음 배포 때 된다. 바로 반영하려면 bash /opt/it-ting/deploy.sh 를 실행한다.
set -euo pipefail
dir="$(cd "$(dirname "$0")" && pwd)"
env_file="${ENV_FILE:-$dir/test/.env}"
example_file="${EXAMPLE_FILE:-$dir/test/.env.example}"

if [ ! -f "$env_file" ]; then
  echo "$env_file 이 없습니다. setup-server.sh를 먼저 실행하세요." >&2
  exit 1
fi

input="$(mktemp)"
output="$(mktemp)"
trap 'rm -f "$input" "$output"' EXIT
# Windows에서 만든 .env의 줄 끝(CR)을 뗀다. 붙은 채로 넣으면 주소가 깨진다.
tr -d '\r' > "$input"

declare -A new_values=()
declare -A written=()
while IFS= read -r line || [ -n "$line" ]; do
  [[ "$line" =~ ^([A-Z0-9_]+)=(.*)$ ]] || continue
  key="${BASH_REMATCH[1]}"
  value="${BASH_REMATCH[2]}"
  if ! grep -qE "^${key}=" "$example_file"; then
    echo "받지 않음: ${key} (test/.env.example에 없는 이름)"
    continue
  fi
  case "$key" in
    TEST_HOST | TEST_MAIL_HOST | MYSQL_*)
      echo "받지 않음: ${key} (이 스크립트로 바꾸지 않는 값)"
      continue
      ;;
  esac
  if [ -z "$value" ]; then
    echo "받지 않음: ${key} (빈 값)"
    continue
  fi
  if [[ "$value" == *'$'* ]]; then
    echo "받지 않음: ${key} (값에 \$가 있다. Compose가 변수로 읽으므로 서버에서 작은따옴표로 감싸 직접 넣는다)"
    continue
  fi
  new_values["$key"]="$value"
done < "$input"

if [ "${#new_values[@]}" -eq 0 ]; then
  echo "넣을 값이 없습니다." >&2
  exit 1
fi

while IFS= read -r line || [ -n "$line" ]; do
  if [[ "$line" =~ ^([A-Z0-9_]+)= ]] && [ -n "${new_values[${BASH_REMATCH[1]}]+set}" ]; then
    key="${BASH_REMATCH[1]}"
    printf '%s=%s\n' "$key" "${new_values[$key]}"
    written["$key"]=1
  else
    printf '%s\n' "$line"
  fi
done < "$env_file" > "$output"
for key in "${!new_values[@]}"; do
  if [ -z "${written[$key]+set}" ]; then
    printf '%s=%s\n' "$key" "${new_values[$key]}" >> "$output"
  fi
done

# 파일을 바꿔치지 않고 내용만 덮어쓴다. 소유자와 권한(600)이 그대로 남는다.
cat "$output" > "$env_file"

echo "넣은 값(이름만):"
for key in "${!new_values[@]}"; do
  echo "  ${key}"
done | sort
echo "아직 빈 값:"
grep -E '^[A-Z0-9_]+=$' "$env_file" | cut -d= -f1 | sed 's/^/  /' || true
