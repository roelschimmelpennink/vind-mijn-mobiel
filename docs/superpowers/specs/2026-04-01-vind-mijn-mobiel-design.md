# Design: Vind Mijn Mobiel (Find My Phone)

**Date:** 2026-04-01
**Status:** Approved

## Overview

A two-component system to ring an Android phone over a local WiFi network when you can't find it. No cloud services, no internet required — works entirely within the local network.

## Components

### 1. Android App (Kotlin)

A small app that runs a persistent HTTP server on the phone.

**MainActivity**
- Displays the phone's current local IP address and port (default: 5000)
- Toggle to start/stop the background RingService
- Minimal UI — primarily a status/info screen

**RingService**
- Android foreground service (survives screen-off, runs persistently)
- Embeds a NanoHTTPD HTTP server on port 5000
- Exposes two endpoints:
  - `GET /ring` — starts playing alarm sound, returns `200 OK`
  - `GET /stop` — stops alarm sound, returns `200 OK`
- All HTTP responses include CORS headers (`Access-Control-Allow-Origin: *`)
- Shows a persistent notification (required for foreground services on Android)

**RingController**
- Plays sound using `RingtoneManager` or `MediaPlayer` routed to `AudioManager.STREAM_ALARM`
- `STREAM_ALARM` bypasses silent and vibrate modes on Android

**Permissions required:**
- `INTERNET`
- `FOREGROUND_SERVICE`
- `WAKE_LOCK`

**Library dependency:**
- NanoHTTPD (lightweight embedded HTTP server, ~50KB)

### 2. Web Client (HTML)

A single self-contained `index.html` file, opened directly in any browser (no local web server needed).

**Features:**
- IP address input field, persisted to `localStorage` (entered once)
- Large "Ring" button — sends `fetch('http://{ip}:5000/ring')`
- "Stop" button — sends `fetch('http://{ip}:5000/stop')`
- Status indicator showing success or unreachable state

**No build step, no framework, no dependencies.**

## HTTP API

| Method | Path    | Action                          | Response     |
|--------|---------|---------------------------------|--------------|
| GET    | /ring   | Start playing alarm sound       | 200 OK       |
| GET    | /stop   | Stop playing alarm sound        | 200 OK       |

All responses include: `Access-Control-Allow-Origin: *`

## Data Flow

```
[Browser on PC]
     │
     │  GET http://192.168.x.x:5000/ring
     ▼
[RingService on Android]
     │
     │  AudioManager.STREAM_ALARM
     ▼
[Loud alarm sound — ignores silent mode]
```

## One-Time Setup

1. Build and install the Android app (via Android Studio or sideload APK)
2. Open the app — note the IP address shown on screen
3. Open `index.html` in a browser, enter the IP once — saved to localStorage
4. Press "Ring" to test

The phone's local IP is stable while connected to the same WiFi network. If it changes, update it in the web page input field.

## Constraints & Assumptions

- Phone and PC must be on the same WiFi network
- The Android app must be running (RingService active) for the ring trigger to work
- Tested target: Android 8.0+ (API 26+)
- No authentication — acceptable for local network home use
