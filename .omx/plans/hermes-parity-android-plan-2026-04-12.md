# Hermes-Style Android Migration Plan

## Requirements Summary

- Make the Android app look and behave more like Hermes Mission Control, especially the app shell and feature navigation.
- Preserve current chat functionality exactly as-is; chat changes are limited to UI presentation and layout.
- Introduce a Hermes-style bottom navigation experience on mobile that surfaces first-class destinations such as dashboard/home, chat, cron jobs, skills, agents, settings, and related utilities.
- Phase the work so UI-shell changes land before data-heavy feature ports.

## Grounded Observations

### Current Android app

- The Android shell currently exposes only three routes: `home`, `room`, and `settings` in [OpenClawAgentsApp.kt](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\app\src\main\java\com\solovision\openclawagents\ui\OpenClawAgentsApp.kt:23).
- The home screen is a single feed combining hero content, agent management, room creation, room cards, and voice preview instead of separate feature areas in [HomeScreen.kt](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\app\src\main\java\com\solovision\openclawagents\ui\screens\HomeScreen.kt:145).
- Chat behavior is centered in `RoomScreen`, including polling, session switching, message filtering, TTS controls, and send flow hooks in [RoomScreen.kt](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\app\src\main\java\com\solovision\openclawagents\ui\screens\RoomScreen.kt:76) and [OpenClawViewModel.kt](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\app\src\main\java\com\solovision\openclawagents\OpenClawViewModel.kt:143).
- The current Android data layer only guarantees agents, rooms, messages, send, create-room, and delete-room operations through `OpenClawRepository` in [OpenClawRepository.kt](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\app\src\main\java\com\solovision\openclawagents\data\OpenClawRepository.kt:33).
- The app already has a strong dark-base theme, but it is still minimal Material 3 without a Hermes-like shell system or destination-specific visual language in [Theme.kt](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\app\src\main\java\com\solovision\openclawagents\ui\theme\Theme.kt:10).

### Hermes reference app

- Hermes uses a central app shell with a shared navigation model across dashboard, chat, tasks, cron, notes, activity, skills, settings, rules, and agents in [App.tsx](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\Reference App\hermes-mission-control-main\src-web\src\App.tsx:24).
- The mobile experience specifically uses a fixed bottom nav backed by a common `mainNavItems` list in [app-shell.tsx](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\Reference App\hermes-mission-control-main\src-web\src\components\app-shell.tsx:25) and [app-shell.tsx](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\Reference App\hermes-mission-control-main\src-web\src\components\app-shell.tsx:545).
- Hermes has dedicated data and UI surfaces for tasks, cron jobs, notes, activity, skills, and rules, backed by explicit API endpoints in [api-client.ts](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\Reference App\hermes-mission-control-main\src-web\src\lib\api-client.ts:70), [api-client.ts](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\Reference App\hermes-mission-control-main\src-web\src\lib\api-client.ts:96), and [api-client.ts](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\Reference App\hermes-mission-control-main\src-web\src\lib\api-client.ts:183).

## Acceptance Criteria

- Android mobile navigation includes a persistent Hermes-style bottom bar with at least the agreed primary destinations.
- Chat still uses the existing room/message/send/polling flow, with no behavioral regression in message loading, sending, session switching, voice playback, or internal-message filtering.
- The former monolithic home surface is split into clearer Hermes-like destinations instead of hiding everything inside one screen.
- Any new destination without backend support is clearly shipped as either read-only, stubbed, or hidden behind a feature flag until data support exists.
- The migration is delivered in phases so shell/UI parity can ship before backend/API parity.

## Recommended Navigation Shape

- `Dashboard`: mission-control summary replacing the current hero-heavy home landing.
- `Chat`: existing chat engine and room/session logic, reskinned only.
- `Agents`: split out agent roster, visibility, order, and voice/profile controls from the current home screen.
- `Cron`: new destination, only if backend support is added; otherwise ship as placeholder/read-only.
- `Skills`: new destination, only if backend support is added; otherwise ship as placeholder/read-only.
- `Settings`: keep current settings, but restyle into Hermes cards and sections.
- Secondary destinations after shell parity: `Activity`, `Notes`, `Rules`, optional `Tasks`.

## Implementation Plan

### Phase 1: Lock the chat boundary

1. Add regression coverage around the current chat flow before touching shell/navigation.
   - Protect `selectRoom`, `startSelectedRoomPolling`, `stopSelectedRoomPolling`, and `sendCurrentMessage` behavior in [OpenClawViewModel.kt](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\app\src\main\java\com\solovision\openclawagents\OpenClawViewModel.kt:134) and [OpenClawViewModel.kt](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\app\src\main\java\com\solovision\openclawagents\OpenClawViewModel.kt:311).
   - Add UI tests around `RoomScreen` session switching, send box, playback controls, and internal-message toggle in [RoomScreen.kt](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\app\src\main\java\com\solovision\openclawagents\ui\screens\RoomScreen.kt:108).
2. Establish a written constraint in code comments or a small architecture note: chat feature work in this migration is presentation-only.

### Phase 2: Build a Hermes-style Android shell

1. Replace the simple `NavHost` wrapper in [OpenClawAgentsApp.kt](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\app\src\main\java\com\solovision\openclawagents\ui\OpenClawAgentsApp.kt:23) with an app shell composable that owns:
   - bottom navigation
   - route metadata
   - shared top app bar rules
   - per-destination content padding/safe area handling
2. Introduce destination modeling, for example:
   - `Dashboard`
   - `Chat`
   - `Agents`
   - `Cron`
   - `Skills`
   - `Settings`
3. Mirror the Hermes pattern of a single navigation definition list from [app-shell.tsx](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\Reference App\hermes-mission-control-main\src-web\src\components\app-shell.tsx:25) so mobile bottom-nav items and route declarations stay in sync.
4. Keep the current `room` route as the underlying chat route initially, but surface it through a chat destination and UI shell instead of direct home-to-room jumps.

### Phase 3: Split the current home screen into Hermes-like destinations

1. Extract a `DashboardScreen` from the current home content.
   - Reuse summary-worthy parts of the hero/status/rooms data from [HomeScreen.kt](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\app\src\main\java\com\solovision\openclawagents\ui\screens\HomeScreen.kt:160).
   - Show high-signal cards: active rooms, visible agents, unread counts, recent activity placeholder, and quick actions.
2. Extract an `AgentsScreen`.
   - Move agent roster, visibility management, order management, and voice configuration out of the home stack from [HomeScreen.kt](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\app\src\main\java\com\solovision\openclawagents\ui\screens\HomeScreen.kt:169).
3. Leave `HomeScreen` behind only as a temporary composition layer during migration, then delete or collapse it once dashboard/agents routes fully replace it.

### Phase 4: Reskin chat without changing behavior

1. Keep the existing room/message state contract and callbacks exactly as passed from [OpenClawAgentsApp.kt](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\app\src\main\java\com\solovision\openclawagents\ui\OpenClawAgentsApp.kt:60).
2. Update only visual structure inside [RoomScreen.kt](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\app\src\main\java\com\solovision\openclawagents\ui\screens\RoomScreen.kt:76) to feel closer to Hermes:
   - Hermes-like header treatment
   - cleaner session switcher styling
   - message-card polish
   - bottom composer styling
   - optional details/debug affordances styled like Mission Control
3. Do not change:
   - repository methods
   - polling cadence
   - send semantics
   - session key logic
   - voice/TTS actions

### Phase 5: Add new feature surfaces behind backend reality

1. Create destination placeholders first for `Cron` and `Skills` so the shell feels complete.
2. Add feature flags or capability states:
   - `available`
   - `read_only`
   - `coming_soon`
3. Only promote a destination to fully interactive when the Android backend can support the matching Hermes endpoint family.
4. Likely backend/API work needed because Hermes expects:
   - tasks endpoints in [api-client.ts](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\Reference App\hermes-mission-control-main\src-web\src\lib\api-client.ts:70)
   - notes/rules/activity endpoints in [api-client.ts](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\Reference App\hermes-mission-control-main\src-web\src\lib\api-client.ts:80)
   - cron endpoints in [api-client.ts](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\Reference App\hermes-mission-control-main\src-web\src\lib\api-client.ts:183)
   - skills endpoints in [api-client.ts](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\Reference App\hermes-mission-control-main\src-web\src\lib\api-client.ts:193)
5. If those APIs do not exist for Android yet, prioritize read-only or stubbed shells instead of inventing fake parity.

### Phase 6: Bring over the Hermes visual system

1. Expand the current theme in [Theme.kt](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\app\src\main\java\com\solovision\openclawagents\ui\theme\Theme.kt:10) into a fuller token system:
   - shell background
   - elevated panels
   - nav active/inactive states
   - status tones
   - badge colors
   - card radii
2. Introduce shared Compose components that map to Hermes concepts:
   - shell scaffold
   - destination icon tab
   - mission-control cards
   - section headers
   - status chips
   - empty states
3. Restyle settings into a more Hermes-like card grid while keeping current settings functionality from [SettingsScreen.kt](C:\Users\jerem\Sandbox\OpenClaw-Agents-Android\app\src\main\java\com\solovision\openclawagents\ui\screens\SettingsScreen.kt:46).

## Delivery Order

1. Chat regression tests
2. New app shell with bottom nav
3. Dashboard and Agents extraction
4. Chat UI-only reskin
5. Settings restyle
6. Placeholder Cron and Skills destinations
7. Real Cron and Skills implementation only after backend support is confirmed
8. Optional later parity for Activity, Notes, Rules, and Tasks

## Risks And Mitigations

### Risk: breaking chat while moving navigation

- Mitigation: test chat behavior first, keep existing `RoomScreen` callbacks, and avoid changing `OpenClawRepository` or `OpenClawViewModel` chat contracts during shell work.

### Risk: importing Hermes features without backend support

- Mitigation: separate shell parity from feature parity; ship placeholders/read-only screens until Android has matching APIs.

### Risk: overstuffed mobile bottom nav

- Mitigation: keep 4-5 primary items in the bottom bar and move lower-priority destinations into an overflow or “More” surface if needed.

### Risk: current home screen logic becomes duplicated across new screens

- Mitigation: extract shared cards/components first, then move route ownership one feature at a time and delete temporary wrappers quickly.

## Verification Steps

1. Run unit tests for `OpenClawViewModel`.
2. Run Compose UI tests covering the chat route before and after shell migration.
3. Manually verify:
   - opening a direct agent chat
   - opening a group room
   - sending a message
   - polling updates still appear
   - session switching still works
   - TTS controls still open and play correctly
4. Manually verify bottom-nav destination changes do not recreate or lose the active chat state unnecessarily.
5. For placeholder destinations, verify unsupported screens clearly communicate availability instead of failing silently.

## Recommendation

Start with a two-track implementation:

- Track A: shell parity
  - bottom nav
  - dashboard
  - agents
  - settings restyle
- Track B: feature parity readiness
  - inventory backend/API support for cron, skills, notes, rules, activity, and tasks

That gets the app looking and feeling much closer to Hermes quickly, while protecting the existing chat engine and avoiding fake feature parity.
