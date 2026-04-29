# Android Talk Mode implementation notes

Android Talk Mode now has two execution paths, in priority order:

## 1. Native realtime Gateway relay (preferred)

When the selected Gateway returns a `talk.realtime.session` result with `transport: "gateway-relay"`, Android opens a persistent authenticated Gateway WebSocket and runs the realtime PCM relay flow:

1. Android opens a persistent authenticated Gateway event client.
2. `talk.realtime.session` is called over that same socket to create the Gateway-owned provider session. Provider API keys/secrets remain on the Gateway.
3. Android keeps that socket open for `talk.realtime.relay` events and relay RPCs, preserving the Gateway `connId` required by the relay.
4. Android records microphone audio with `AudioRecord` using the Gateway-provided PCM16 input sample rate.
5. Microphone chunks are sent as base64 PCM via `talk.realtime.relayAudio`.
6. Gateway relay audio events are decoded and streamed through `AudioTrack` using the Gateway-provided PCM16 output sample rate.
7. `clear`, `mark`, `transcript`, `error`, and `close` relay events are handled on-device.
8. `openclaw_agent_consult` tool calls are answered by sending the question to the selected OpenClaw session and returning the latest assistant reply through `talk.realtime.relayToolResult`.
9. Stopping Talk sends `talk.realtime.relayStop`, closes the persistent socket, releases the microphone, and releases playback.

Supported realtime transport in this Android branch:

- ✅ `gateway-relay` with `pcm16` input and `pcm16` output
- ❌ `webrtc-sdp` is not implemented natively on Android in this app
- ❌ `json-pcm-websocket` / Google Live browser-token WebSocket is not implemented on Android in this app
- ❌ `managed-room` is not implemented

## 2. Phased fallback

If realtime relay is unavailable, unsupported, or fails to start, the app preserves the previous phased Talk Mode:

1. Android `SpeechRecognizer` listens continuously while Talk is enabled.
2. Final transcripts are sent to the selected OpenClaw session with `chat.send`.
3. The latest assistant reply is fetched from `chat.history` through the existing repository flow.
4. Spoken replies are synthesized through Gateway `talk.speak` with `outputFormat=pcm_24000` and played locally.
5. Android system TTS is used only if `talk.speak` is unavailable or returns unusable audio.

## Runtime/service lifecycle

Talk Mode starts the foreground service with the Android `microphone` service type, and stops/demotes it when Talk is disabled. Manual Mic is separate and mutually exclusive; it stops on foreground loss.

## Verification

Build gate for this branch:

```bash
./gradlew :app:assembleDebug
```

Manual realtime verification requires a Gateway configured with a realtime Talk provider that returns `transport: "gateway-relay"` from `talk.realtime.session`. Legacy or WebRTC-only Gateways should visibly fall back to the phased `chat.send` + `talk.speak` path.
