---
name: backend-design-first
description: Use this skill when implementing or modifying backend features in this repository. It enforces a design-first workflow, requires documentation output under docs/ai, and makes the agent explain trade-offs, risks, and testing after code changes.
---

# Purpose

This skill exists to keep backend work educational, reviewable, and resume-friendly.

Use this skill for:
- new backend features
- API design
- database schema changes
- cache / concurrency / idempotency changes
- order, inventory, payment, notification, auth, and permission logic
- refactors that affect domain boundaries or engineering structure

Do not use this skill for:
- tiny typo-only changes
- pure formatting fixes
- trivial comment edits

# Required workflow

## Step 1: understand and scope
Before editing code, summarize:
- goal
- scope / out of scope
- impacted modules
- important assumptions
- risks

## Step 2: design before implementation
Produce a concise design that covers:
- domain objects
- API endpoints or message contracts
- data model and indexes
- state transitions if any
- concurrency / idempotency / cache implications
- security / authorization implications
- testing strategy

## Step 3: document the design
Before writing the design note, read and follow:
- `docs/templates/design-template.md`

Then create or update a design note under:
- `docs/ai/design/`

Suggested filename:
- `YYYY-MM-DD-<topic>-design.md`

Keep the same section order as the template unless the task clearly needs a different structure.

## Step 4: implement the smallest viable slice
Make the smallest change set that closes the target loop.
Avoid broad speculative abstractions.

## Step 5: verify
If the repo supports it, run the smallest relevant verification:
- unit tests
- integration tests
- build
- lint
- targeted manual validation notes

## Step 6: document implementation
Before writing the implementation note, read and follow:
- `docs/templates/implementation-note-template.md`

Then create or update an implementation note under:
- `docs/ai/implementation/`

Suggested filename:
- `YYYY-MM-DD-<topic>-implementation.md`

The implementation note must answer:
1. What problem was solved
2. Why this design was chosen
3. What alternatives were considered
4. Why alternatives were not used
5. What validation was performed
6. What limitations / next steps remain

## Step 7: ADR when needed
If the task introduces a meaningful architectural or engineering trade-off, first read:
- `docs/templates/adr-template.md`

Then add:
- `docs/ai/adr/YYYY-MM-DD-<topic>.md`

Examples:
- choosing optimistic locking vs distributed lock
- choosing synchronous flow vs event-driven flow
- choosing monolith module boundary or service split

# Output format after completion

Always end with:
1. Design summary
2. Code change summary
3. Rationale
4. Alternatives
5. Risks / follow-ups
6. Updated documentation files
7. Verification results
