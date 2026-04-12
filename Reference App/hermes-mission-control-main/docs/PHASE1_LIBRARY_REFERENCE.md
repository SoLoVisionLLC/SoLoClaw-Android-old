# Mission Control Dashboard — Phase 1 Library Reference

> **Status:** Complete (Forge, 2026-04)
> **Purpose:** Index the actual Phase 1 library files and cross-reference existing workspace documentation.

---

## Primary Implementation Files

These files constitute the current Phase 1 implementation under `/home/solo/hermes-mission-control/`:

### Frontend (TypeScript)

| File | Purpose |
|------|---------|
| `src-web/src/types/index.ts` | Shared TypeScript contracts — task enums, agent/session/message shapes, cron job types, app config |
| `src-web/src/lib/api-client.ts` | Typed fetch client for all `/api/*` endpoints; handles platform-aware API base routing |
| `src-web/src/lib/platform.ts` | Tauri shell detection, external link routing, bundled-app defaults |
| `src-web/src/hooks/use-store.ts` | Zustand store — app-wide state for config, agents, groups, tasks, activity, notes, sessions |
| `src-web/src/lib/utils.ts` | General utilities |
| `src-web/src/lib/themes.ts` | Theme constants |

### Backend (Rust / Tauri)

| File | Purpose |
|------|---------|
| `src-tauri/src/models.rs` | Rust-side data structures mirroring frontend types |
| `src-tauri/src/store.rs` | Persistent app state — `~/.hermes/mission-control/store.json` |
| `src-tauri/src/commands.rs` | Tauri command handlers — cron sync, agent discovery, CRUD operations |
| `src-tauri/src/lib.rs` | Bootstrap — plugin registration, command surface wiring, embedded API server startup on port 1421 |

---

## Legacy Workspace Library Reference (Pre-Migration)

These library docs were created during the OpenClaw era but remain relevant as the **architectural reference** for agent interaction patterns:

| Document | Library | Author |
|----------|---------|--------|
| `~/.openclaw/workspace-docs/agents-ts.md` | `agents.ts` — AgentsDetector for OpenClaw gateway | Forge |
| `~/.openclaw/workspace-docs/notion-ts.md` | `notion.ts` — NotionClient for Notion API v1 | Forge |
| `~/.openclaw/workspace-docs/openclaw-ts.md` | `openclaw.ts` — OpenClawClient WebSocket with session management | Forge |
| `~/.openclaw/workspace-forge/mission-control/libs/index.ts` | Barrel export for all three libraries | Forge |

These files document the **agent gateway pattern** and **Notion CRUD** that the current Mission Control system builds upon. They are the historical record of the library layer that preceded the Hermes-native implementation.

---

## Consolidated Index

| Topic | File(s) |
|-------|---------|
| How UI routes API calls | `src-web/src/lib/api-client.ts` + `src-web/src/lib/platform.ts` |
| What data shapes the frontend expects | `src-web/src/types/index.ts` |
| Where app state lives (desktop/native) | `src-tauri/src/store.rs` |
| How the backend exposes commands | `src-tauri/src/commands.rs`, `src-tauri/src/lib.rs` |
| How Zustand manages UI state | `src-web/src/hooks/use-store.ts` |
| Legacy agent gateway integration | `~/.openclaw/workspace-docs/openclaw-ts.md` |
| Legacy Notion integration | `~/.openclaw/workspace-docs/notion-ts.md` |
| Legacy agent detection | `~/.openclaw/workspace-docs/agents-ts.md` |
| Full technical narrative | `PHASE1_TECHNICAL_DOCS.md` |

---

## Relation to Phase 2

Phase 2 builds on this foundation. The current store uses full reload-after-mutation (no optimistic updates). This is intentional for the current product stage — see `PHASE1_TECHNICAL_DOCS.md` §Known Constraints for context before extending the store.