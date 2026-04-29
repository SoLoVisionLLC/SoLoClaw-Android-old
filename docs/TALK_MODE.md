# Android Talk Mode implementation notes

This branch implements natural audio Talk Mode using the Gateway-backed loop documented by OpenClaw:

1. Android `SpeechRecognizer` listens continuously while Talk is enabled.
2. Final transcripts are sent to the selected OpenClaw session with `chat.send`.
3. The latest assistant reply is fetched from `chat.history` through the existing repository flow.
4. Spoken replies are synthesized through Gateway `talk.speak` with `outputFormat=pcm_24000` and played locally.
5. Android system TTS is used only if `talk.speak` is unavailable or returns unusable audio.

## Realtime relay seam

Gateway realtime primitives (`talk.realtime.session`, `talk.realtime.relayAudio`, `talk.realtime.relayStop`) were inspected, but full native PCM streaming was left out of this branch because the current app transport is request/response WebSocket-per-RPC and closes each socket after one response. Realtime relay needs a long-lived subscribed socket that can receive `talk.realtime.relay` events while streaming microphone PCM chunks. The app now has a clean seam at the Talk controller/playback layer; adding realtime should start by introducing a persistent Gateway client rather than bolting streaming onto the existing one-shot `request()` helper.

## Runtime/service lifecycle

Talk Mode starts the foreground service with the `microphone` service type, and stops/demotes it when Talk is disabled. Manual Mic is separate and mutually exclusive; it stops on foreground loss.
