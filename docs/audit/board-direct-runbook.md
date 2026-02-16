# Direct Project Board Creation Runbook (No `gh` CLI)

This repository now includes `scripts/create_delivery_board.sh` to create/update the Project v2 board directly with GitHub GraphQL API (`curl` + `jq`).

## What it does
- Creates (or reuses) Project board: `CRM-HLM — Delivery Board (Auto)`.
- Ensures Project fields exist:
  - `Status` (Upcoming / In Progress / Done / Decision Needed)
  - `Module` (MOD-01..MOD-13)
  - `Priority` (P0/P1/P2)
  - `Requirement ID` (text)
- Adds missing **draft items** from `docs/requirements/requirements.normalized.yml`.
- Maps statuses from `docs/audit/requirements-audit.md`:
  - `DONE` -> `Done`
  - `PARTIAL` -> `In Progress`
  - `MISSING` -> `Upcoming`
  - `DECISION NEEDED` -> `Decision Needed`

## Required environment
- `GITHUB_TOKEN`: token with Projects permissions.
- `GH_OWNER`: GitHub org/user login where the board is hosted.

Optional:
- `BOARD_TITLE`: override board title.

## Usage
```bash
export GITHUB_TOKEN=<token>
export GH_OWNER=<org-or-user>
./scripts/create_delivery_board.sh
```

## Idempotency behavior
- Reuses existing board by title.
- Reuses existing fields by name.
- Skips draft item creation if an item with same title (`REQ-###: <title>`) already exists.
