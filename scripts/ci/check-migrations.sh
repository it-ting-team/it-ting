#!/usr/bin/env bash
# 기준 브랜치에 이미 있는 Flyway 마이그레이션 파일을 고치거나 지우거나 이름을 바꿨는지 확인한다.
# 새 마이그레이션 파일을 더하는 것만 통과한다. 고칠 것이 있으면 새 번호의 마이그레이션을 더한다(AGENTS.md의 DB 절).
# 테스트 전용 마이그레이션(backend/src/test/resources/db/migration)은 매번 새 DB에서 돌므로 검사하지 않는다.
# 사용: bash scripts/ci/check-migrations.sh <기준 커밋 또는 브랜치> [<비교할 커밋, 기본 HEAD>]
# 예:   bash scripts/ci/check-migrations.sh origin/dev
set -euo pipefail

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  echo "사용: $0 <기준 커밋 또는 브랜치> [<비교할 커밋>]" >&2
  exit 2
fi

base="$1"
head="${2:-HEAD}"
dir="backend/src/main/resources/db/migration"

# 세 점(...)은 갈라진 지점부터 head까지, 곧 PR이 바꾼 것만 본다.
changes=$(git diff --name-status -M "${base}...${head}" -- "$dir")

bad=$(printf '%s\n' "$changes" | awk -F'\t' 'NF && $1 != "A"')

if [ -n "$bad" ]; then
  echo "::error::이미 병합된 마이그레이션 파일은 고치거나 지우거나 이름을 바꿀 수 없습니다. 새 번호의 마이그레이션을 더하세요."
  printf '%s\n' "$bad" | while IFS=$'\t' read -r status path rest; do
    case "$status" in
      M*) echo "  고침: $path" ;;
      D*) echo "  지움: $path" ;;
      R*) echo "  이름 바꿈: $path → $rest" ;;
      *)  echo "  ${status}: $path $rest" ;;
    esac
  done
  exit 1
fi

added=$(printf '%s\n' "$changes" | awk -F'\t' 'NF' | wc -l | tr -d ' ')
echo "마이그레이션 보호 통과: 새 파일 ${added}개, 바뀐 기존 파일 없음"
