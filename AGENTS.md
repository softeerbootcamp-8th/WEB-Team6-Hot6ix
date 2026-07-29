# Hot6ix Codex Instructions

Before doing any work in this repository, read `CLAUDE.md` completely and
treat it as the repository-wide team instructions. The file name is historical;
its rules apply equally to Codex.

- When `CLAUDE.md` routes to `.claude/skills/hot6ix-development/references/`,
  read only the references relevant to the task.
- Repo-specific reusable workflows live under `.agents/skills/`. Use the
  matching `hot6ix-*` skill for development, commits, GitHub issues, pull
  requests, and verification.
- The destructive-command guard is configured for Codex in
  `.codex/hooks.json`. Treat it as a first guardrail, not a replacement for the
  safety rules in `CLAUDE.md`.

## Code Review Rules

- Flag changes that violate the public API, authentication, database, or
  realtime-event contracts described by the applicable Hot6ix instructions.
- Flag weakened or removed tests, hand-edited generated frontend files, and
  success states shown before the server confirms them.
- Report concrete findings with file and line references; do not invent
  speculative issues without repository evidence.
