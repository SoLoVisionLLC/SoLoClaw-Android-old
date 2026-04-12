# Mission Control Dashboard — Phase 1 Technical Docs

This document backfills the missing Phase 1 technical documentation for Hermes Mission Control.

Phase 1 covers the core application plumbing that makes the dashboard usable across desktop, web, and Android:

- shared frontend domain types
- frontend API client wrappers
- Zustand application store orchestration
- Rust/Tauri in-process store and persistence
- Tauri command registration and embedded HTTP API startup
- environment-aware platform routing for native shell vs remote API

## Scope

This documentation reflects the current implementation in `/home/solo/hermes-mission-control`.

Primary files:

- `src-web/src/types/index.ts`
- `src-web/src/lib/api-client.ts`
- `src-web/src/hooks/use-store.ts`
- `src-web/src/lib/platform.ts`
- `src-tauri/src/lib.rs`
- `src-tauri/src/models.rs`
- `src-tauri/src/store.rs`
- `src-tauri/src/commands.rs`
- `ANDROID_WORKFLOW.md`

## Architecture Summary

Mission Control uses a split architecture:

1. **React + TypeScript frontend** renders the UI and owns client-side interaction flow.
2. **Rust + Tauri shell** owns local persistence, command execution, and embedded API hosting.
3. **HTTP API layer** provides a uniform interface for web and Android clients.
4. **Platform detection** decides when to call a local relative API vs the explicit remote endpoint `https://hermes.solobot.cloud`.

That gives the project three practical runtime modes:

- **Desktop Tauri**: bundled frontend + Rust backend + embedded API on port `1421`
- **Web**: frontend talks to deployed API
- **Android**: bundled native shell with explicit remote API base instead of localhost assumptions

## Phase 1 Core Libraries

### 1) Shared frontend types

File: `src-web/src/types/index.ts`

Purpose:

- defines the TypeScript contracts used across screens, store, and API client
- mirrors the Rust backend model layer so JSON payloads stay aligned
- centralizes enums for task status, task priority, activity types, session/message shapes, cron jobs, and app config

Why it matters:

- prevents each screen from inventing its own payload format
- keeps frontend and backend evolution tractable
- makes the API client thin because shape expectations already exist in one place

Key model groups:

- agents and groups
- sessions and messages
- tasks
- activity log
- notes
- Hermes instances
- app config
- cron jobs and cron job runs
- skills hub file metadata

### 2) Frontend API client

File: `src-web/src/lib/api-client.ts`

Purpose:

- exposes one typed function per backend endpoint
- centralizes `fetch` behavior and error handling in `apiFetch<T>()`
- computes the correct API base depending on runtime environment

Important implementation details:

- `API_BASE` is derived from `globalThis.__MISSION_CONTROL_API_BASE__`
- on Android inside the Tauri shell it falls back to `https://hermes.solobot.cloud`
- all calls normalize trailing slashes away
- non-2xx responses throw explicit errors with status code and response text
- `204` responses are normalized to `undefined`

Primary exported client groups:

- agents: discovery, profile update, group assignment
- groups: CRUD operations
- tasks: CRUD operations
- notes: CRUD operations
- sessions/messages: create session, rename session, get messages, send message
- activity: fetch timeline
- cron jobs: CRUD + runs + run-now
- config: read app config
- skills: list/check/update/search/install/delete/file access

Why it matters:

- screens and stores do not need to know endpoint wiring details
- API routing logic is isolated to one file
- Android-specific remote fallback is documented and explicit

### 3) Frontend application store

File: `src-web/src/hooks/use-store.ts`

Purpose:

- uses Zustand as the main UI-facing orchestration layer
- stores shared app state for config, agents, groups, tasks, activities, notes, sessions, and instances
- wraps the API client with higher-level app actions

Initialization flow:

1. fetch config
2. set sidebar state from config
3. load groups, agents, tasks, activity, notes, and sessions in parallel
4. attempt agent discovery if enabled and no agents were loaded

Why it matters:

- each screen can stay presentational
- error/loading state is centralized
- mutations trigger canonical reloads after writes, reducing local drift

Practical design note:

The store currently favors full reload-after-mutation behavior over optimistic updates. That is slower than a fully normalized client cache, but simpler and less error-prone during the current product stage.

### 4) Platform helpers

File: `src-web/src/lib/platform.ts`

Purpose:

- detects whether the UI is running in a Tauri shell
- distinguishes bundled app behavior from browser-hosted behavior
- opens external URLs through the Tauri shell plugin when available
- resolves the default API base for packaged builds

Important behavior:

- bundled Tauri apps default to `https://hermes.solobot.cloud`
- browser-hosted web builds default to relative/empty base handling
- external links use `@tauri-apps/plugin-shell` when possible and fall back to `window.open`

This file is part of the native-shell strategy because it makes packaged app behavior explicit instead of guessing from browser defaults.

### 5) Rust model layer

File: `src-tauri/src/models.rs`

Purpose:

- defines the authoritative Rust-side data structures serialized to the frontend
- mirrors the TypeScript model layer
- provides constructors for core entities like `AgentStatus`, `Task`, `Activity`, `Note`, and `Session`

Important types:

- `AgentStatus`, `AgentGroup`, `AgentState`
- `Session`, `Message`, `ToolCall`, `MessageRole`
- `Task`, `TaskStatus`, `TaskPriority`
- `Activity`, `ActivityType`
- `Note`
- `HermesInstance`, `InstanceStatus`
- `CronJob`, `CronJobRun`

Why it matters:

- this is the serialization boundary for desktop/native features
- frontend types should track this file closely
- drift here will surface as broken UI expectations or failed JSON parsing

### 6) Rust persistent app store

File: `src-tauri/src/store.rs`

Purpose:

- keeps in-process application state for desktop/native runtime
- persists state to `~/.hermes/mission-control/store.json`
- provides a default bootstrap state when no persisted file exists

Stored collections include:

- config
- agents
- sessions and messages
- tasks
- activities
- notes
- Hermes instances
- groups
- cron jobs and runs

Important behavior:

- `load_or_default()` attempts to hydrate from disk
- `persist()` creates parent directories and writes pretty-printed JSON
- `add_activity()` enforces `activity_log_max_entries` trimming

Why it matters:

- desktop/native state survives restarts
- the persistence path is stable and outside the repo
- test coverage exists for session/message round-tripping

### 7) Tauri bootstrap and command wiring

File: `src-tauri/src/lib.rs`

Purpose:

- creates the shared `AppStore`
- registers Tauri plugins
- registers the command surface exposed to the frontend
- starts the embedded HTTP API server

Important runtime details:

- embedded API port defaults to `1421`
- the app manages a shared `Arc<RwLock<AppStore>>`
- in debug builds it opens devtools automatically

Registered capability areas include:

- agent status and discovery
- sessions/messages
- tasks
- activity log
- notes
- config
- instance discovery/connection
- group management
- cron jobs and cron run history

Why it matters:

- this file is the backend composition root
- the previous localhost mismatch issue is understandable from here: the server is explicitly started on `1421`

### 8) Command layer

File: `src-tauri/src/commands.rs`

Purpose:

- implements the actual Tauri command handlers and cron sync logic
- bridges model mutations to store persistence and external integrations

Notable documented behavior seen in current code:

- syncs cron jobs from `~/.hermes/cron/jobs.json`
- normalizes Hermes cron file shape when older schedule structures are encountered
- persists transformed cron job records back to the file system

Why it matters:

- command behavior is where local data management becomes product behavior
- this is the right place to inspect when a UI action appears to succeed visually but does not persist

## Runtime/API Routing Notes

The current routing pattern is deliberate:

- **Desktop debug/web dev** can use local relative paths
- **Embedded API server** runs on `1421`
- **Android** must not assume localhost on-device
- **Bundled/native shells** use explicit remote API base when necessary

Relevant references:

- `src-web/src/lib/api-client.ts`
- `src-web/src/lib/platform.ts`
- `src-tauri/src/lib.rs`
- `ANDROID_WORKFLOW.md`

This is the key technical lesson from the Android fix: packaging the UI is not enough unless the app also knows where its API actually lives.

## Data Flow Overview

### Task flow

1. UI screen triggers a store action.
2. Zustand store calls a typed API client helper.
3. API hits either relative `/api/...` or explicit remote base.
4. Rust backend mutates `AppStore`.
5. Backend persists updated state where applicable.
6. Frontend reloads canonical collections.

### Session/message flow

1. Session is created or selected in the frontend.
2. Messages are requested from `/api/sessions/:id/messages`.
3. Sending a message hits `/api/messages`.
4. Backend updates session/message state.
5. UI reloads or renders updated conversation state.

### Cron flow

1. UI reads cron jobs from the backend.
2. Backend can sync from `~/.hermes/cron/jobs.json`.
3. Existing Hermes cron metadata is normalized.
4. Store and cron file stay aligned.

## Known Constraints / Technical Debt

- README previously described the project at a high level but did not document the Phase 1 library responsibilities.
- The original task references old library filenames like `openclaw.ts`, `notion.ts`, and `agents.ts`; the current repo now centers on `api-client.ts`, `platform.ts`, `use-store.ts`, and Rust-side store/model files.
- Frontend state management currently reloads full collections after writes instead of using fine-grained optimistic updates.
- Some old task wording appears to come from pre-migration workspace naming and should be treated as historical context, not current file truth.

## Verification Checklist

Phase 1 documentation backfill should now include at minimum:

- [x] where shared types live
- [x] how API routing is decided
- [x] what Zustand owns
- [x] what Rust models/store own
- [x] where persistence is written
- [x] which file starts the embedded API server
- [x] Android/native-shell routing rationale

## Recommended Next Documentation Work

1. Add endpoint-by-endpoint HTTP API reference for `/api/*` routes.
2. Document each screen and its store dependencies.
3. Add a persistence schema note for `store.json`.
4. Add release/deployment notes that connect `README.md`, `ANDROID_WORKFLOW.md`, and live production operations.
