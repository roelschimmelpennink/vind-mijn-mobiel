# Internet Mode (ntfy.sh) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add internet-mode ring/stop using ntfy.sh so the phone can be triggered even when it's on mobile data.

**Architecture:** A new `NtfyListener` class opens a persistent SSE connection to `https://ntfy.sh/{topic}/sse` and calls `RingController` when "ring" or "stop" messages arrive. `RingService` starts it alongside the existing `PhoneRingServer`. The web client gains two new buttons that POST to ntfy.sh. The existing WiFi mode is untouched.

**Tech Stack:** Kotlin (Android 8+ / API 26+), `HttpURLConnection` (no new dependencies), vanilla HTML/CSS/JS, ntfy.sh free public service

---

## File Structure

```
android-app/app/src/main/java/com/vindmijnmobiel/
  NtfyListener.kt              — new: SSE listener + static line parser
  RingService.kt               — modified: start/stop NtfyListener, read topic from SharedPreferences
  MainActivity.kt              — modified: generate/display ntfy topic
android-app/app/src/main/res/
  layout/activity_main.xml     — modified: add tvNtfyTopic TextView
  values/strings.xml           — modified: add ntfy_topic_label string
android-app/app/src/test/java/com/vindmijnmobiel/
  NtfyListenerTest.kt          — new: unit tests for SSE line parsing
web-client/
  index.html                   — modified: add ntfy topic input + internet buttons
```

---

## Task 1: NtfyListener (TDD)

**Files:**
- Create: `android-app/app/src/test/java/com/vindmijnmobiel/NtfyListenerTest.kt`
- Create: `android-app/app/src/main/java/com/vindmijnmobiel/NtfyListener.kt`

- [ ] **Step 1: Write the failing test**

Create `android-app/app/src/test/java/com/vindmijnmobiel/NtfyListenerTest.kt`:

```kotlin
package com.vindmijnmobiel

import org.junit.Assert.*
import org.junit.Test

class NtfyListenerTest {

    class FakeRingPlayer : RingPlayer {
        var started = false
        var stopped = false
        override val isRinging: Boolean get() = started && !stopped
        override fun startRinging() { started = true }
        override fun stopRinging() { stopped = true }
    }

    @Test
    fun `handleLine ring JSON calls startRinging`() {
        val player = FakeRingPlayer()
        NtfyListener.handleLine("""data: {"id":"abc","message":"ring"}""", player)
        assertTrue(player.started)
        assertFalse(player.stopped)
    }

    @Test
    fun `handleLine stop JSON calls stopRinging`() {
        val player = FakeRingPlayer()
        NtfyListener.handleLine("""data: {"id":"abc","message":"stop"}""", player)
        assertTrue(player.stopped)
        assertFalse(player.started)
    }

    @Test
    fun `handleLine event line is ignored`() {
        val player = FakeRingPlayer()
        NtfyListener.handleLine("event: message", player)
        assertFalse(player.started)
        assertFalse(player.stopped)
    }

    @Test
    fun `handleLine keepalive line is ignored`() {
        val player = FakeRingPlayer()
        NtfyListener.handleLine(": keepalive", player)
        assertFalse(player.started)
        assertFalse(player.stopped)
    }

    @Test
    fun `handleLine empty line is ignored`() {
        val player = FakeRingPlayer()
        NtfyListener.handleLine("", player)
        assertFalse(player.started)
        assertFalse(player.stopped)
    }

    @Test
    fun `handleLine unrecognized data is ignored`() {
        val player = FakeRingPlayer()
        NtfyListener.handleLine("""data: {"id":"abc","message":"hello"}""", player)
        assertFalse(player.started)
        assertFalse(player.stopped)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

In Android Studio: right-click `NtfyListenerTest` → Run. Or:
```
gradlew.bat :app:test
```
Expected: compilation error — `NtfyListener` does not exist yet.

- [ ] **Step 3: Create `android-app/app/src/main/java/com/vindmijnmobiel/NtfyListener.kt`**

```kotlin
package com.vindmijnmobiel

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class NtfyListener(
    private val topic: String,
    private val player: RingPlayer
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        running = true
        thread = Thread {
            while (running) {
                try {
                    connect()
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    if (running) Thread.sleep(5000)
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun connect() {
        val conn = URL("https://ntfy.sh/$topic/sse").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 0
        try {
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            var line: String?
            while (running && reader.readLine().also { line = it } != null) {
                handleLine(line!!, player)
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun handleLine(line: String, player: RingPlayer) {
            if (!line.startsWith("data:")) return
            val data = line.removePrefix("data:").trim()
            when {
                data.contains("\"ring\"") -> player.startRinging()
                data.contains("\"stop\"") -> player.stopRinging()
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
gradlew.bat :app:test
```
Expected: 6 existing tests + 6 new NtfyListenerTest tests = 12 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add android-app/app/src/main/java/com/vindmijnmobiel/NtfyListener.kt
git add android-app/app/src/test/java/com/vindmijnmobiel/NtfyListenerTest.kt
git commit -m "feat: NtfyListener SSE client for internet-mode ring trigger"
```

---

## Task 2: Android app wiring (RingService + MainActivity + resources)

**Files:**
- Modify: `android-app/app/src/main/res/values/strings.xml`
- Modify: `android-app/app/src/main/res/layout/activity_main.xml`
- Modify: `android-app/app/src/main/java/com/vindmijnmobiel/MainActivity.kt`
- Modify: `android-app/app/src/main/java/com/vindmijnmobiel/RingService.kt`

- [ ] **Step 1: Add string resource to `android-app/app/src/main/res/values/strings.xml`**

Add one line inside `<resources>`:
```xml
<string name="ntfy_topic_label">Ntfy topic (enter in web client):</string>
```

Full file after change:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Vind Mijn Mobiel</string>
    <string name="phone_address">Phone address (enter in web client):</string>
    <string name="start_service">Start ring server</string>
    <string name="stop_service">Stop ring server</string>
    <string name="notification_title">Vind Mijn Mobiel</string>
    <string name="notification_text">Ring server is active on port 5000</string>
    <string name="channel_id">ring_service_channel</string>
    <string name="ntfy_topic_label">Ntfy topic (enter in web client):</string>
</resources>
```

- [ ] **Step 2: Update layout `android-app/app/src/main/res/layout/activity_main.xml`**

Add a label + topic display TextView below the IP section (change `marginBottom` on `tvIpAddress` from 32dp to 24dp, add the two new views before the buttons):

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center"
    android:orientation="vertical"
    android:padding="24dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/phone_address"
        android:textSize="16sp" />

    <TextView
        android:id="@+id/tvIpAddress"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:layout_marginBottom="24dp"
        android:textSize="22sp"
        android:textStyle="bold" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/ntfy_topic_label"
        android:textSize="16sp" />

    <TextView
        android:id="@+id/tvNtfyTopic"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:layout_marginBottom="32dp"
        android:textIsSelectable="true"
        android:textSize="14sp" />

    <Button
        android:id="@+id/btnStartService"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/start_service" />

    <Button
        android:id="@+id/btnStopService"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:text="@string/stop_service" />

</LinearLayout>
```

- [ ] **Step 3: Update `android-app/app/src/main/java/com/vindmijnmobiel/MainActivity.kt`**

```kotlin
package com.vindmijnmobiel

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        createNotificationChannel()
        requestNotificationPermission()

        val ip = getLocalIpAddress()
        findViewById<TextView>(R.id.tvIpAddress).text = "$ip:${RingService.PORT}"

        val topic = getOrCreateNtfyTopic()
        findViewById<TextView>(R.id.tvNtfyTopic).text = topic

        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            startForegroundService(Intent(this, RingService::class.java))
        }

        findViewById<Button>(R.id.btnStopService).setOnClickListener {
            stopService(Intent(this, RingService::class.java))
        }
    }

    private fun getOrCreateNtfyTopic(): String {
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return prefs.getString("ntfy_topic", null) ?: run {
            val topic = UUID.randomUUID().toString()
            prefs.edit().putString("ntfy_topic", topic).apply()
            topic
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            getString(R.string.channel_id),
            getString(R.string.notification_title),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1
                )
            }
        }
    }

    private fun getLocalIpAddress(): String {
        return try {
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}
```

- [ ] **Step 4: Update `android-app/app/src/main/java/com/vindmijnmobiel/RingService.kt`**

```kotlin
package com.vindmijnmobiel

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class RingService : Service() {

    private var server: PhoneRingServer? = null
    private var ntfyListener: NtfyListener? = null
    private lateinit var ringController: RingController

    override fun onCreate() {
        super.onCreate()
        ringController = RingController(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (server != null) return START_STICKY
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        server = PhoneRingServer(PORT, ringController).also { it.start() }

        val topic = getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .getString("ntfy_topic", null)
        if (topic != null) {
            ntfyListener = NtfyListener(topic, ringController).also { it.start() }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        ntfyListener?.stop()
        ringController.stopRinging()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, getString(R.string.channel_id))
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openApp)
            .build()
    }

    companion object {
        const val PORT = 5000
        const val NOTIFICATION_ID = 1
    }
}
```

- [ ] **Step 5: Build to verify**

```
gradlew.bat :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add android-app/app/src/main/res/values/strings.xml
git add android-app/app/src/main/res/layout/activity_main.xml
git add android-app/app/src/main/java/com/vindmijnmobiel/MainActivity.kt
git add android-app/app/src/main/java/com/vindmijnmobiel/RingService.kt
git commit -m "feat: wire NtfyListener into RingService, show topic in MainActivity"
```

---

## Task 3: Web client — internet mode buttons

**Files:**
- Modify: `web-client/index.html`

- [ ] **Step 1: Replace `web-client/index.html` with the updated version**

```html
<!DOCTYPE html>
<html lang="nl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Vind Mijn Mobiel</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: sans-serif;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            background: #f0f0f0;
            gap: 16px;
        }
        h1 { font-size: 2rem; }
        h2 { font-size: 1rem; color: #555; font-weight: normal; }
        .row {
            display: flex;
            gap: 8px;
        }
        .divider {
            width: 100%;
            max-width: 400px;
            border: none;
            border-top: 1px solid #ccc;
            margin: 8px 0;
        }
        input {
            padding: 8px 12px;
            font-size: 16px;
            border: 1px solid #ccc;
            border-radius: 4px;
            width: 240px;
        }
        button {
            padding: 10px 20px;
            font-size: 16px;
            border: none;
            border-radius: 6px;
            cursor: pointer;
        }
        #btnSaveIp      { background: #1976d2; color: white; }
        #btnRing        { background: #d32f2f; color: white; font-size: 1.5rem; padding: 20px 60px; }
        #btnStop        { background: #616161; color: white; }
        #btnSaveNtfy    { background: #1976d2; color: white; }
        #btnRingInternet { background: #c62828; color: white; font-size: 1.5rem; padding: 20px 40px; }
        #btnStopInternet { background: #424242; color: white; }
        #status         { font-size: 0.9rem; color: #555; height: 20px; }
    </style>
</head>
<body>
    <h1>Vind Mijn Mobiel</h1>

    <h2>WiFi (zelfde netwerk)</h2>
    <div class="row">
        <input id="ipInput" type="text" placeholder="192.168.1.x" />
        <button id="btnSaveIp" onclick="saveIp()">Save</button>
    </div>
    <button id="btnRing" onclick="sendCommand('ring')">Ring</button>
    <button id="btnStop" onclick="sendCommand('stop')">Stop</button>

    <hr class="divider">

    <h2>Internet (mobiele data)</h2>
    <div class="row">
        <input id="ntfyInput" type="text" placeholder="ntfy topic (UUID)" />
        <button id="btnSaveNtfy" onclick="saveNtfy()">Save</button>
    </div>
    <button id="btnRingInternet" onclick="sendNtfy('ring')">Ring</button>
    <button id="btnStopInternet" onclick="sendNtfy('stop')">Stop</button>

    <div id="status"></div>

    <script>
        const ipInput   = document.getElementById('ipInput');
        const ntfyInput = document.getElementById('ntfyInput');
        const status    = document.getElementById('status');

        ipInput.value   = localStorage.getItem('phoneIp')   || '';
        ntfyInput.value = localStorage.getItem('ntfyTopic') || '';

        function saveIp() {
            localStorage.setItem('phoneIp', ipInput.value.trim());
            setStatus('IP saved.');
        }

        function saveNtfy() {
            localStorage.setItem('ntfyTopic', ntfyInput.value.trim());
            setStatus('Ntfy topic saved.');
        }

        async function sendCommand(cmd) {
            const ip = ipInput.value.trim();
            if (!ip) { setStatus('Enter the phone IP address first.'); return; }
            setStatus('Sending...');
            try {
                const res = await fetch(`http://${ip}:5000/${cmd}`);
                setStatus(res.ok
                    ? (cmd === 'ring' ? 'Ringing!' : 'Stopped.')
                    : `Server error: ${res.status}`);
            } catch {
                setStatus('Could not reach phone. Is the ring server running?');
            }
        }

        async function sendNtfy(cmd) {
            const topic = ntfyInput.value.trim();
            if (!topic) { setStatus('Enter the ntfy topic first.'); return; }
            setStatus('Sending...');
            try {
                const res = await fetch(`https://ntfy.sh/${topic}`, {
                    method: 'POST',
                    body: cmd
                });
                setStatus(res.ok
                    ? (cmd === 'ring' ? 'Ringing! (internet)' : 'Stopped. (internet)')
                    : `Server error: ${res.status}`);
            } catch {
                setStatus('Could not reach ntfy.sh. Check internet connection.');
            }
        }

        function setStatus(msg) {
            status.textContent = msg;
        }
    </script>
</body>
</html>
```

- [ ] **Step 2: Open `web-client/index.html` in a browser — verify both sections render correctly: WiFi section at top, Internet section below the divider**

- [ ] **Step 3: Commit**

```bash
git add web-client/index.html
git commit -m "feat: add internet mode section to web client (ntfy.sh)"
```

---

## Task 4: End-to-end integration test

Manual verification — requires phone with the updated app installed.

- [ ] **Step 1: Install updated app**

In Android Studio: Run ▶ (with phone connected via USB). Or:
```
gradlew.bat :app:installDebug
```

- [ ] **Step 2: Note the ntfy topic on the phone**

Open the app — below the IP address you'll see a UUID, e.g.:
`a3f8c2d1-7b4e-4f2a-9c1d-3e5f7a8b2c4d`

Tap **Start ring server**.

- [ ] **Step 3: Configure the web client**

Open `web-client/index.html`. Paste the UUID into the ntfy topic field → click **Save**.

- [ ] **Step 4: Test WiFi mode (existing)**

Phone on same WiFi. Click **Ring** (WiFi section) → phone rings. Click **Stop** → stops.

- [ ] **Step 5: Test internet mode (new)**

Keep the phone on WiFi but simulate internet mode: click **Ring** in the Internet section → phone should ring (ntfy.sh relays the message to the SSE listener). Click **Stop** → stops.

- [ ] **Step 6: Test with phone on mobile data**

Disable WiFi on the phone (mobile data only). Click **Ring** in the Internet section → phone should still ring. Click **Stop** → stops.
