# OpenClaw Agents Android

A brand new Android app for OpenClaw, designed from scratch for mobile-first agent collaboration.

## Product vision

OpenClaw Agents is not a simple chat wrapper.
It is a mobile command surface for:
- talking to any agent directly
- creating multi-agent group rooms
- watching agent collaboration unfold in one thread
- hearing responses with text-to-speech
- managing work with a polished, modern mobile UX

## Core product pillars

1. Agent-first communication
2. Group collaboration rooms
3. Voice-native interactions
4. Premium modern design
5. Fast, readable conversation flows

## Initial v1 scope

- Home screen with agent roster and active rooms
- Direct 1:1 agent conversations
- Group room creation flow
- Conversation screen with multi-agent message attribution
- TTS playback controls per message and room
- Modern dark-first interface

## Status

Fresh repo scaffold in progress.

## Gateway configuration

The app uses the real Hermes/OpenClaw gateway transport when `OPENCLAW_GATEWAY_URL` and `OPENCLAW_SESSION_KEY` are configured. Defaults point at the production gateway route and Orion's main session:

- `OPENCLAW_GATEWAY_URL=wss://gateway.solobot.cloud`
- `OPENCLAW_SESSION_KEY=agent:orion:main`

Set `OPENCLAW_API_KEY` from an environment variable or Gradle property when a shared gateway token is required. Do not commit API keys or secrets.

Example local build:

```bash
OPENCLAW_API_KEY=... ./gradlew :app:assembleDebug
```

The fake repository fallback is only used when the gateway URL or session key is deliberately left blank.
