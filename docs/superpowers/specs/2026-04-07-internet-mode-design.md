# Design: Internet Mode via ntfy.sh

**Date:** 2026-04-07
**Status:** Approved

## Overview

Extend "Vind Mijn Mobiel" to ring the phone when it is on mobile data (not on the same WiFi as the laptop). Uses ntfy.sh as a free, no-account pub/sub relay. The existing WiFi mode is untouched — this is purely additive.

## Use Case

- Laptop stays on home WiFi
- Phone may be on home WiFi (existing mode works) or on mobile data (new internet mode needed)
- User triggers ring from the web client on the laptop

## Architecture

```
[Browser on PC]
      │
      │  POST https://ntfy.sh/{topic}  body: "ring"
      ▼
[ntfy.sh relay — free public service]
      │
      │  SSE stream push
      ▼
[Android app — NtfyListener in RingService]
      │
      │  RingController.startRinging()
      ▼
[Loud alarm sound — bypasses silent mode]
```

## Components

### 1. Android — NtfyListener

New class `NtfyListener` in `com.vindmijnmobiel`.

- Runs a single background thread (not a coroutine — no new dependencies)
- Opens `GET https://ntfy.sh/{topic}/sse` via `HttpURLConnection`
- Reads the SSE stream line by line
- When a `data:` line contains `"ring"` → calls `player.startRinging()`
- When a `data:` line contains `"stop"` → calls `player.stopRinging()`
- If the connection drops or throws, waits 5 seconds and reconnects
- Stopped cleanly by setting a volatile `running` flag to false and interrupting the thread

Constructor: `NtfyListener(topic: String, player: RingPlayer)`

Methods:
- `start()` — starts the background thread
- `stop()` — sets running = false, interrupts thread

### 2. Android — RingService (modified)

- Reads the ntfy topic from `SharedPreferences` on `onCreate()`
- Creates and starts a `NtfyListener(topic, ringController)` alongside the existing `PhoneRingServer`
- Calls `ntfyListener.stop()` in `onDestroy()`

### 3. Android — MainActivity (modified)

- On first launch, generates a UUID topic and saves it to `SharedPreferences` key `"ntfy_topic"`
- Displays the topic below the IP address so the user can copy it into the web client

### 4. Web client — index.html (modified)

- New input field for ntfy topic (id `ntfyInput`), saved to `localStorage` key `"ntfyTopic"`
- New **Ring (internet)** button → `sendNtfy('ring')`
- New **Stop (internet)** button → `sendNtfy('stop')`
- `sendNtfy(cmd)`: fetches `POST https://ntfy.sh/{topic}` with `body: cmd`
- Status indicator reused for feedback

The existing WiFi buttons and IP input are unchanged.

## ntfy.sh API

| Method | URL | Body | Effect |
|--------|-----|------|--------|
| POST | `https://ntfy.sh/{topic}` | `ring` | Triggers ring on phone |
| POST | `https://ntfy.sh/{topic}` | `stop` | Stops ring on phone |
| GET | `https://ntfy.sh/{topic}/sse` | — | SSE stream (Android subscribes here) |

ntfy.sh allows cross-origin requests — no CORS issues from the browser.

## Topic Security

The topic name is a UUID (e.g. `a3f8c2d1-7b4e-4f2a-9c1d-3e5f7a8b2c4d`). Anyone who knows the topic can ring the phone — security through obscurity, acceptable for personal home use. Topic is stored in `SharedPreferences` on Android and `localStorage` in the browser.

## One-Time Setup

1. Open the Android app — note the ntfy topic shown on screen
2. Open `index.html` — enter the topic in the ntfy field, click Save
3. Click **Ring (internet)** to test — phone should ring regardless of which network it's on

## Constraints & Assumptions

- Requires internet connectivity on the phone (either WiFi or mobile data)
- ntfy.sh must be reachable (free public service, generally reliable)
- `NtfyListener` runs as long as `RingService` is active — the user must have started the ring server
- No retry limit on reconnect — the listener keeps trying indefinitely while the service runs
- Android 8.0+ (API 26+), unchanged from original
