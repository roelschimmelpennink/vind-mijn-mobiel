# Vind Mijn Mobiel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android app that exposes an HTTP ring endpoint, and a local HTML file that lets you trigger it from any browser on the same WiFi network.

**Architecture:** The Android app runs a NanoHTTPD foreground service on port 5000. It exposes `GET /ring` and `GET /stop` endpoints. A single `index.html` file uses `fetch()` to call those endpoints. The `RingPlayer` interface decouples audio logic from HTTP logic, making the server testable without Android framework dependencies.

**Tech Stack:** Kotlin (Android 8+ / API 26+), NanoHTTPD 2.3.1, JUnit 4, vanilla HTML/CSS/JS

---

## File Structure

```
android-app/
  settings.gradle.kts                                  — project name + module declaration
  build.gradle.kts                                     — root plugin versions
  app/
    build.gradle.kts                                   — dependencies + SDK config
    src/
      main/
        AndroidManifest.xml                            — permissions + service declaration
        java/com/vindmijnmobiel/
          RingPlayer.kt                                — interface: startRinging / stopRinging / isRinging
          RingController.kt                            — implements RingPlayer via AudioManager + MediaPlayer
          PhoneRingServer.kt                           — NanoHTTPD subclass, routes /ring and /stop
          RingService.kt                               — foreground service, owns server + controller
          MainActivity.kt                              — shows local IP, start/stop service buttons
        res/
          layout/activity_main.xml                    — IP display + two buttons
          values/strings.xml                           — all string resources
      test/
        java/com/vindmijnmobiel/
          PhoneRingServerTest.kt                       — JVM unit tests for HTTP routing + CORS
web-client/
  index.html                                           — single-file browser UI
```

---

## Task 1: Android project scaffolding

**Files:**
- Create: `android-app/settings.gradle.kts`
- Create: `android-app/build.gradle.kts`
- Create: `android-app/app/build.gradle.kts`
- Create: `android-app/app/src/main/AndroidManifest.xml`
- Create: `android-app/app/src/main/res/values/strings.xml`

- [ ] **Step 1: Create `android-app/settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "VindMijnMobiel"
include(":app")
```

- [ ] **Step 2: Create `android-app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
```

- [ ] **Step 3: Create `android-app/app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vindmijnmobiel"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vindmijnmobiel"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 4: Create `android-app/app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:theme="@style/Theme.AppCompat.Light.DarkActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".RingService"
            android:foregroundServiceType="mediaPlayback"
            android:exported="false" />

    </application>

</manifest>
```

- [ ] **Step 5: Create `android-app/app/src/main/res/values/strings.xml`**

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
</resources>
```

- [ ] **Step 6: Open Android Studio → Open existing project → select `android-app/`. Let Gradle sync complete. Verify no errors in the sync output.**

- [ ] **Step 7: Commit**

```bash
git add android-app/
git commit -m "feat: android project scaffold with NanoHTTPD dependency"
```

---

## Task 2: RingPlayer interface + PhoneRingServer (TDD)

**Files:**
- Create: `android-app/app/src/main/java/com/vindmijnmobiel/RingPlayer.kt`
- Create: `android-app/app/src/main/java/com/vindmijnmobiel/PhoneRingServer.kt`
- Create: `android-app/app/src/test/java/com/vindmijnmobiel/PhoneRingServerTest.kt`

- [ ] **Step 1: Write the failing test — create `android-app/app/src/test/java/com/vindmijnmobiel/PhoneRingServerTest.kt`**

```kotlin
package com.vindmijnmobiel

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class PhoneRingServerTest {

    class FakeRingPlayer : RingPlayer {
        var started = false
        var stopped = false
        override val isRinging: Boolean get() = started && !stopped
        override fun startRinging() { started = true }
        override fun stopRinging() { stopped = true }
    }

    private lateinit var fakePlayer: FakeRingPlayer
    private lateinit var server: PhoneRingServer

    @Before
    fun setUp() {
        fakePlayer = FakeRingPlayer()
        server = PhoneRingServer(15000, fakePlayer)
        server.start()
        Thread.sleep(100)
    }

    @After
    fun tearDown() {
        server.stop()
    }

    private fun get(path: String): Pair<Int, String> {
        val conn = URL("http://localhost:15000$path").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        val code = conn.responseCode
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return code to body
    }

    @Test
    fun `GET ring calls startRinging and returns 200 OK`() {
        val (code, body) = get("/ring")
        assertEquals(200, code)
        assertEquals("OK", body)
        assertTrue(fakePlayer.started)
    }

    @Test
    fun `GET stop calls stopRinging and returns 200 OK`() {
        val (code, body) = get("/stop")
        assertEquals(200, code)
        assertEquals("OK", body)
        assertTrue(fakePlayer.stopped)
    }

    @Test
    fun `GET ring response includes CORS header`() {
        val conn = URL("http://localhost:15000/ring").openConnection() as HttpURLConnection
        conn.connect()
        val cors = conn.getHeaderField("Access-Control-Allow-Origin")
        conn.disconnect()
        assertEquals("*", cors)
    }

    @Test
    fun `unknown path returns 404`() {
        val conn = URL("http://localhost:15000/unknown").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(404, conn.responseCode)
        conn.disconnect()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

In Android Studio: right-click `PhoneRingServerTest` → Run. Or in terminal:
```bash
cd android-app && ./gradlew :app:test
```
Expected: compilation failure — `RingPlayer` and `PhoneRingServer` do not exist yet.

- [ ] **Step 3: Create `android-app/app/src/main/java/com/vindmijnmobiel/RingPlayer.kt`**

```kotlin
package com.vindmijnmobiel

interface RingPlayer {
    fun startRinging()
    fun stopRinging()
    val isRinging: Boolean
}
```

- [ ] **Step 4: Create `android-app/app/src/main/java/com/vindmijnmobiel/PhoneRingServer.kt`**

NanoHTTPD 2.3.1 uses package `fi.iki.elonen`.

```kotlin
package com.vindmijnmobiel

import fi.iki.elonen.NanoHTTPD

class PhoneRingServer(port: Int, private val player: RingPlayer) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val response = when (session.uri) {
            "/ring" -> {
                player.startRinging()
                newFixedLengthResponse("OK")
            }
            "/stop" -> {
                player.stopRinging()
                newFixedLengthResponse("OK")
            }
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found"
            )
        }
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cd android-app && ./gradlew :app:test
```
Expected: 4 tests PASS. If on Windows cmd: `gradlew.bat :app:test`

- [ ] **Step 6: Commit**

```bash
git add android-app/app/src/
git commit -m "feat: RingPlayer interface + PhoneRingServer with routing and CORS"
```

---

## Task 3: RingController

**Files:**
- Create: `android-app/app/src/main/java/com/vindmijnmobiel/RingController.kt`

> Note: `RingController` uses Android framework APIs (`MediaPlayer`, `AudioAttributes`, `RingtoneManager`) that cannot run in JVM unit tests. It is verified manually on device in Task 6.

- [ ] **Step 1: Create `android-app/app/src/main/java/com/vindmijnmobiel/RingController.kt`**

```kotlin
package com.vindmijnmobiel

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager

class RingController(private val context: Context) : RingPlayer {

    private var mediaPlayer: MediaPlayer? = null

    override val isRinging: Boolean
        get() = mediaPlayer?.isPlaying == true

    override fun startRinging() {
        if (isRinging) return
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(context, alarmUri)
            isLooping = true
            prepare()
            start()
        }
    }

    override fun stopRinging() {
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
    }
}
```

- [ ] **Step 2: Verify tests still pass**

```bash
cd android-app && ./gradlew :app:test
```
Expected: 4 tests PASS (same as before — no new tests for this class).

- [ ] **Step 3: Commit**

```bash
git add android-app/app/src/main/java/com/vindmijnmobiel/RingController.kt
git commit -m "feat: RingController plays alarm sound via AudioManager.STREAM_ALARM"
```

---

## Task 4: RingService (foreground service)

**Files:**
- Create: `android-app/app/src/main/java/com/vindmijnmobiel/RingService.kt`

- [ ] **Step 1: Create `android-app/app/src/main/java/com/vindmijnmobiel/RingService.kt`**

```kotlin
package com.vindmijnmobiel

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class RingService : Service() {

    private var server: PhoneRingServer? = null
    private lateinit var ringController: RingController

    override fun onCreate() {
        super.onCreate()
        ringController = RingController(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        server = PhoneRingServer(PORT, ringController).also { it.start() }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
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

- [ ] **Step 2: Verify build succeeds**

```bash
cd android-app && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add android-app/app/src/main/java/com/vindmijnmobiel/RingService.kt
git commit -m "feat: RingService foreground service starts NanoHTTPD on port 5000"
```

---

## Task 5: MainActivity + layout

**Files:**
- Create: `android-app/app/src/main/java/com/vindmijnmobiel/MainActivity.kt`
- Create: `android-app/app/src/main/res/layout/activity_main.xml`

- [ ] **Step 1: Create `android-app/app/src/main/res/layout/activity_main.xml`**

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
        android:layout_marginBottom="32dp"
        android:textSize="22sp"
        android:textStyle="bold" />

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

- [ ] **Step 2: Create `android-app/app/src/main/java/com/vindmijnmobiel/MainActivity.kt`**

```kotlin
package com.vindmijnmobiel

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
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

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        createNotificationChannel()
        requestNotificationPermission()

        val ip = getLocalIpAddress()
        findViewById<TextView>(R.id.tvIpAddress).text = "$ip:${RingService.PORT}"

        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            startForegroundService(Intent(this, RingService::class.java))
        }

        findViewById<Button>(R.id.btnStopService).setOnClickListener {
            stopService(Intent(this, RingService::class.java))
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

- [ ] **Step 3: Build the APK**

```bash
cd android-app && ./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`. APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Commit**

```bash
git add android-app/app/src/main/java/com/vindmijnmobiel/MainActivity.kt
git add android-app/app/src/main/res/layout/activity_main.xml
git commit -m "feat: MainActivity shows local IP and start/stop controls"
```

---

## Task 6: Web client

**Files:**
- Create: `web-client/index.html`

- [ ] **Step 1: Create `web-client/index.html`**

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
        .ip-row {
            display: flex;
            gap: 8px;
        }
        input {
            padding: 8px 12px;
            font-size: 16px;
            border: 1px solid #ccc;
            border-radius: 4px;
            width: 180px;
        }
        button {
            padding: 10px 20px;
            font-size: 16px;
            border: none;
            border-radius: 6px;
            cursor: pointer;
        }
        #btnSave  { background: #1976d2; color: white; }
        #btnRing  { background: #d32f2f; color: white; font-size: 1.5rem; padding: 20px 60px; margin-top: 16px; }
        #btnStop  { background: #616161; color: white; }
        #status   { font-size: 0.9rem; color: #555; height: 20px; }
    </style>
</head>
<body>
    <h1>Vind Mijn Mobiel</h1>

    <div class="ip-row">
        <input id="ipInput" type="text" placeholder="192.168.1.x" />
        <button id="btnSave" onclick="saveIp()">Save</button>
    </div>

    <button id="btnRing" onclick="sendCommand('ring')">Ring</button>
    <button id="btnStop" onclick="sendCommand('stop')">Stop</button>

    <div id="status"></div>

    <script>
        const ipInput = document.getElementById('ipInput');
        const status  = document.getElementById('status');

        ipInput.value = localStorage.getItem('phoneIp') || '';

        function saveIp() {
            localStorage.setItem('phoneIp', ipInput.value.trim());
            setStatus('IP saved.');
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

        function setStatus(msg) {
            status.textContent = msg;
        }
    </script>
</body>
</html>
```

- [ ] **Step 2: Open `web-client/index.html` in a browser and verify it renders — you should see the title, an IP input, and Ring/Stop buttons.**

- [ ] **Step 3: Commit**

```bash
git add web-client/index.html
git commit -m "feat: web client HTML with ring/stop buttons and localStorage IP"
```

---

## Task 7: End-to-end integration test

Manual verification steps — requires an Android device on the same WiFi as the PC.

- [ ] **Step 1: Install the app on your Android device**

Connect the phone via USB. In Android Studio: Run → Run 'app'. Or install the APK manually:
```bash
adb install android-app/app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 2: Start the ring server on the phone**

Open the "Vind Mijn Mobiel" app. Note the IP:port displayed (e.g. `192.168.1.42:5000`). Tap "Start ring server". Verify the persistent notification appears in the status bar.

- [ ] **Step 3: Verify the server is reachable from the PC**

Open a browser on your PC and navigate to:
```
http://192.168.1.42:5000/ring
```
Expected: browser shows `OK` and the phone plays a loud alarm sound (even if on silent).

- [ ] **Step 4: Verify stop works**

Navigate to:
```
http://192.168.1.42:5000/stop
```
Expected: browser shows `OK` and the alarm stops.

- [ ] **Step 5: Test via the web client**

Open `web-client/index.html` in the browser. Enter `192.168.1.42` in the IP field and click Save. Click "Ring" — phone should ring. Click "Stop" — alarm should stop. Refresh the page and verify the IP is still there (localStorage).

- [ ] **Step 6: Test silent mode bypass**

Put the phone on silent or vibrate. Click "Ring" from the web client. The alarm should still sound — `STREAM_ALARM` bypasses silent mode on Android.
