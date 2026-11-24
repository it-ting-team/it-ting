#!/usr/bin/env bash
# PR의 병합 흐름이 docs/branch-strategy.md와 맞는지 확인한다.
#   test로 가는 PR: dev에서만
#   main으로 가는 PR: test나 hotfix/*에서만
#   그 밖(dev 등): 검사하지 않는다
# 사용: bash scripts/ci/check-merge-flow.sh <기준 브랜치> <원본 브랜치>
# 예:   bash scripts/ci/check-merge-flow.sh main test
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "사용: $0 <기준 브랜치> <원본 브랜치>" >&2
  exit 2
fi

base="$1"
head="$2"

fail() {
  echo "::error::${head} → ${base} 병합은 받지 않습니다. $1 (docs/branch-strategy.md의 병합 흐름)"
  exit 1
}

case "$base" in
  test)
    [ "$head" = "dev" ] || fail "test로 가는 PR은 dev에서만 엽니다."
    ;;
  main)
    if [ "$head" != "test" ] && [[ "$head" != hotfix/* ]]; then
      fail "main으로 가는 PR은 test나 hotfix/* 브랜치에서만 엽니다."
    fi
    ;;
esac

echo "병합 흐름 통과: ${head} → ${base}"
