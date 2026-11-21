@AGENTS.md

## Claude Code 전용

- 스킬은 `.claude/skills/`에 있다. `.agents/skills/`의 사본이므로 고칠 때는 원본을 고치고 `bash scripts/sync-agent-skills.sh`를 실행한다.
- Playwright MCP 설정은 `.mcp.json`에 있다. 처음 실행하면 사용 승인을 묻는다.
