#!/usr/bin/env bash
# 스킬 원본(.agents/skills)을 Claude Code용 사본(.claude/skills)으로 복사한다.
# 스킬을 고친 뒤 저장소 루트에서 실행한다: bash scripts/sync-agent-skills.sh
set -euo pipefail

cd "$(dirname "$0")/.."

rm -rf .claude/skills
mkdir -p .claude
cp -R .agents/skills .claude/skills

echo ".agents/skills → .claude/skills 복사 완료"
