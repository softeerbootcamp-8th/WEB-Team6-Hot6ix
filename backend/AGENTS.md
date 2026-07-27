# Hot6ix Backend Codex Instructions

Read `backend/CLAUDE.md` completely before backend work and treat it as the
instructions for this subtree. Also follow the repository root `AGENTS.md` and
`CLAUDE.md`.

For implementation or review, use the `hot6ix-development` skill and load its
backend, contract, domain, or workflow references only as needed.

## Code Review Rules

- Flag business logic or transactions placed in controllers.
- Flag contract changes, unsafe read-modify-write flows, or success events
  emitted before transaction commit unless the change includes explicit team
  approval and matching tests.
- Do not report absent JPA or MySQL configuration as a defect while persistence
  remains intentionally unintroduced.
