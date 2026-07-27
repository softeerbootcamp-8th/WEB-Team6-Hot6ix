# Hot6ix Frontend Codex Instructions

Read `frontend/CLAUDE.md` completely before frontend work and treat it as the
instructions for this subtree. Also follow the repository root `AGENTS.md` and
`CLAUDE.md`.

For implementation or review, use the `hot6ix-development` skill and load its
frontend, contract, domain, or workflow references only as needed. Read
`frontend/docs/CONVENTIONS.md` when the task touches frontend conventions.

## Code Review Rules

- Flag manual edits to `src/api/generated/**` or `src/routeTree.gen.ts`.
- Flag direct Axios calls that bypass the shared generated API flow.
- Flag missing cleanup for subscriptions, listeners, or timers and client-side
  auction completion that is not confirmed by the server.
