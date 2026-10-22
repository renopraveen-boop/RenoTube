package com.renotube.tv.remote

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WebRemoteServer"
const val DEFAULT_WEB_REMOTE_PORT = 8080

@Singleton
class WebRemoteServer @Inject constructor(
    private val ambilightRepository: com.renotube.tv.ambilight.AmbilightRepository
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val threadPool = Executors.newFixedThreadPool(4)
    private var serverJob: Job? = null
    var activePort: Int = DEFAULT_WEB_REMOTE_PORT
        private set

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                // Try port 8080 first, fallback to 8888
                serverSocket = try {
                    activePort = DEFAULT_WEB_REMOTE_PORT
                    ServerSocket(DEFAULT_WEB_REMOTE_PORT)
                } catch (e: Exception) {
                    activePort = 8888
                    ServerSocket(8888)
                }
                isRunning = true
                Log.i(TAG, "Web Remote HTTP Server started successfully on port $activePort")

                while (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
                    val client = serverSocket!!.accept()
                    threadPool.execute {
                        handleClient(client)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Web Remote server error: ${e.message}")
                }
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val out = PrintWriter(s.getOutputStream())

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0]
                val fullUri = parts[1]
                val path = if (fullUri.contains("?")) fullUri.substringBefore("?") else fullUri
                val query = if (fullUri.contains("?")) fullUri.substringAfter("?") else ""

                val params = mutableMapOf<String, String>()
                if (query.isNotEmpty()) {
                    query.split("&").forEach { pair ->
                        val kv = pair.split("=")
                        if (kv.size == 2) {
                            params[URLDecoder.decode(kv[0], "UTF-8")] = URLDecoder.decode(kv[1], "UTF-8")
                        }
                    }
                }

                when {
                    path == "/" || path == "/index.html" -> {
                        val html = getRemoteHtml()
                        sendResponse(out, 200, "text/html; charset=utf-8", html)
                    }
                    path == "/api/key" -> {
                        val key = params["code"] ?: ""
                        if (key.isNotEmpty()) {
                            RemoteCommandDispatcher.dispatchKey(key)
                            sendResponse(out, 200, "application/json", "{\"status\":\"ok\",\"key\":\"$key\"}")
                        } else {
                            sendResponse(out, 400, "application/json", "{\"error\":\"missing key code\"}")
                        }
                    }
                    path == "/api/search" -> {
                        val q = params["q"] ?: ""
                        if (q.isNotEmpty()) {
                            RemoteCommandDispatcher.dispatchSearch(q)
                            sendResponse(out, 200, "application/json", "{\"status\":\"ok\",\"query\":\"$q\"}")
                        } else {
                            sendResponse(out, 400, "application/json", "{\"error\":\"missing query\"}")
                        }
                    }
                    path == "/api/status" -> {
                        sendResponse(out, 200, "application/json", "{\"status\":\"running\",\"app\":\"RenoTube TV\",\"port\":$activePort}")
                    }
                    path == "/api/player/status" || path == "/api/player" -> {
                        val json = RemoteCommandDispatcher.getPlayerStatusJson()
                        sendResponse(out, 200, "application/json", json)
                    }
                    path == "/api/player/seek" -> {
                        val ms = params["ms"]?.toLongOrNull()
                        if (ms != null) {
                            RemoteCommandDispatcher.seekToMs(ms)
                            sendResponse(out, 200, "application/json", "{\"status\":\"ok\",\"seekMs\":$ms}")
                        } else {
                            sendResponse(out, 400, "application/json", "{\"error\":\"missing ms parameter\"}")
                        }
                    }
                    path == "/api/player/pause" -> {
                        RemoteCommandDispatcher.pausePlayback()
                        sendResponse(out, 200, "application/json", "{\"status\":\"ok\",\"paused\":true}")
                    }
                    path == "/api/player/play" || path == "/api/player/resume" -> {
                        RemoteCommandDispatcher.resumePlayback()
                        sendResponse(out, 200, "application/json", "{\"status\":\"ok\",\"playing\":true}")
                    }
                    path == "/api/play" -> {
                        val videoId = params["videoId"] ?: ""
                        if (videoId.isNotEmpty()) {
                            RemoteCommandDispatcher.dispatchPlay(videoId)
                            sendResponse(out, 200, "application/json", "{\"status\":\"ok\",\"videoId\":\"$videoId\"}")
                        } else {
                            sendResponse(out, 400, "application/json", "{\"error\":\"missing videoId parameter\"}")
                        }
                    }
                    path == "/api/ambilight/dump" -> {
                        val json = ambilightRepository.getLatestLedsJson()
                        sendResponse(out, 200, "application/json", json)
                    }
                    path == "/api/ambilight" -> {
                        val enabledParam = params["enabled"]
                        val ipParam = params["ip"]
                        val portParam = params["port"]
                        val testParam = params["test"]
                        val topParam = params["top"]?.toIntOrNull()
                        val bottomParam = params["bottom"]?.toIntOrNull()
                        val leftParam = params["left"]?.toIntOrNull()
                        val rightParam = params["right"]?.toIntOrNull()

                        var cur = ambilightRepository.config.value
                        if (enabledParam != null) {
                            cur = cur.copy(enabled = enabledParam.toBoolean())
                        }
                        if (!ipParam.isNullOrBlank()) {
                            cur = cur.copy(wledIp = ipParam.trim())
                        }
                        if (!portParam.isNullOrBlank()) {
                            val p = portParam.toIntOrNull()
                            if (p != null) cur = cur.copy(wledPort = p)
                        }
                        if (topParam != null) cur = cur.copy(topLeds = topParam)
                        if (bottomParam != null) cur = cur.copy(bottomLeds = bottomParam)
                        if (leftParam != null) cur = cur.copy(leftLeds = leftParam)
                        if (rightParam != null) cur = cur.copy(rightLeds = rightParam)

                        ambilightRepository.updateConfig(cur)

                        if (testParam == "pattern" || testParam == "true") {
                            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                ambilightRepository.sendCalibrationPattern()
                            }
                        } else if (enabledParam?.toBoolean() == true) {
                            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                ambilightRepository.setWarmWhite()
                            }
                        }

                        val cfg = ambilightRepository.config.value
                        sendResponse(
                            out,
                            200,
                            "application/json",
                            "{\"enabled\":${cfg.enabled},\"ip\":\"${cfg.wledIp}\",\"port\":${cfg.wledPort},\"totalLeds\":${cfg.totalLeds},\"leftLeds\":${cfg.leftLeds},\"topLeds\":${cfg.topLeds},\"rightLeds\":${cfg.rightLeds},\"bottomLeds\":${cfg.bottomLeds}}"
                        )
                    }
                    else -> {
                        sendResponse(out, 404, "text/plain", "Not Found")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Client handle exception: ${e.message}")
        }
    }

    private fun sendResponse(out: PrintWriter, statusCode: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        out.print("HTTP/1.1 $statusCode OK\r\n")
        out.print("Content-Type: $contentType\r\n")
        out.print("Content-Length: ${bytes.size}\r\n")
        out.print("Connection: close\r\n")
        out.print("Access-Control-Allow-Origin: *\r\n")
        out.print("\r\n")
        out.print(body)
        out.flush()
    }

    companion object {
        fun getLocalIpAddress(context: Context): String {
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
                if (ipInt != 0) {
                    return String.format(
                        Locale.US,
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        (ipInt shr 8) and 0xff,
                        (ipInt shr 16) and 0xff,
                        (ipInt shr 24) and 0xff
                    )
                }
            } catch (_: Exception) {}

            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    val addrs = iface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress ?: "127.0.0.1"
                        }
                    }
                }
            } catch (_: Exception) {}
            return "127.0.0.1"
        }
    }

    private fun getRemoteHtml(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>RenoTube TV Remote</title>
<style>
  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
  body {
    margin: 0;
    padding: 16px;
    background: #0f0f0f;
    color: #f1f1f1;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    display: flex;
    flex-direction: column;
    align-items: center;
    min-height: 100vh;
  }
  .header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 16px;
  }
  .header h1 {
    font-size: 20px;
    font-weight: 700;
    margin: 0;
    color: #ff0000;
  }
  .header span {
    font-size: 13px;
    background: #272727;
    padding: 3px 8px;
    border-radius: 12px;
    color: #aaa;
  }
  .card {
    background: #181818;
    border: 1px solid #282828;
    border-radius: 20px;
    padding: 16px;
    width: 100%;
    max-width: 380px;
    box-shadow: 0 8px 24px rgba(0,0,0,0.5);
    margin-bottom: 16px;
  }
  .search-box {
    display: flex;
    gap: 8px;
  }
  .search-box input {
    flex: 1;
    background: #222;
    border: 1px solid #383838;
    border-radius: 12px;
    padding: 12px 14px;
    color: #fff;
    font-size: 15px;
    outline: none;
  }
  .search-box input:focus {
    border-color: #ff0000;
  }
  .search-box button {
    background: #ff0000;
    color: #fff;
    border: none;
    border-radius: 12px;
    padding: 12px 16px;
    font-weight: bold;
    font-size: 14px;
    cursor: pointer;
  }
  .dpad-container {
    display: flex;
    flex-direction: column;
    align-items: center;
    margin: 20px 0;
  }
  .dpad {
    position: relative;
    width: 240px;
    height: 240px;
    background: #222;
    border-radius: 50%;
    border: 2px solid #333;
    box-shadow: inset 0 2px 8px rgba(0,0,0,0.6);
  }
  .dpad-btn {
    position: absolute;
    background: #2a2a2a;
    border: none;
    color: #fff;
    font-size: 22px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: background 0.1s, transform 0.1s;
  }
  .dpad-btn:active {
    background: #ff0000;
    transform: scale(0.92);
  }
  .dpad-up {
    top: 6px;
    left: 80px;
    width: 80px;
    height: 64px;
    border-radius: 40px 40px 10px 10px;
  }
  .dpad-down {
    bottom: 6px;
    left: 80px;
    width: 80px;
    height: 64px;
    border-radius: 10px 10px 40px 40px;
  }
  .dpad-left {
    left: 6px;
    top: 80px;
    width: 64px;
    height: 80px;
    border-radius: 40px 10px 10px 40px;
  }
  .dpad-right {
    right: 6px;
    top: 80px;
    width: 64px;
    height: 80px;
    border-radius: 10px 40px 40px 10px;
  }
  .dpad-center {
    top: 76px;
    left: 76px;
    width: 88px;
    height: 88px;
    border-radius: 50%;
    background: #383838;
    border: 2px solid #555;
    font-size: 15px;
    font-weight: bold;
    box-shadow: 0 4px 10px rgba(0,0,0,0.4);
  }
  .row-btns {
    display: flex;
    justify-content: space-around;
    width: 100%;
    margin-top: 10px;
    gap: 8px;
  }
  .section-title {
    font-size: 11px;
    font-weight: 700;
    color: #888;
    text-transform: uppercase;
    letter-spacing: 1px;
    margin-bottom: 8px;
    width: 100%;
    text-align: left;
  }
  .grid-btns {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
    width: 100%;
    margin-bottom: 4px;
  }
  .settings-btn {
    background: #242424;
    border: 1px solid #383838;
    color: #fff;
    padding: 10px 4px;
    border-radius: 10px;
    font-size: 12px;
    font-weight: 600;
    cursor: pointer;
    transition: background 0.1s, transform 0.1s;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
  }
  .settings-btn:active {
    background: #ff0000;
    transform: scale(0.95);
  }
  .settings-btn span {
    font-size: 16px;
  }
  .action-btn {
    flex: 1;
    background: #262626;
    border: 1px solid #3a3a3a;
    color: #eee;
    padding: 12px;
    border-radius: 12px;
    font-size: 14px;
    font-weight: 600;
    cursor: pointer;
    transition: background 0.1s, transform 0.1s;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
  }
  .action-btn:active {
    background: #444;
    transform: scale(0.95);
  }
  .media-btn {
    background: #202020;
    font-size: 15px;
    padding: 14px;
  }
  .feedback {
    height: 20px;
    color: #4CAF50;
    font-size: 13px;
    font-weight: bold;
    text-align: center;
    margin-top: 8px;
  }
</style>
</head>
<body>

<div class="header">
  <h1>▶ RenoTube</h1>
  <span>TV Remote</span>
</div>

<!-- Mobile Keyboard Direct Search -->
<div class="card">
  <div class="search-box">
    <input type="text" id="searchInput" placeholder="Type on TV keyboard / Search..." autocomplete="off"/>
    <button onclick="sendSearch()">Search</button>
  </div>
</div>

<!-- Playback Controls & Quick Settings Direct Access -->
<div class="card">
  <div class="section-title">Playback & Settings</div>
  <div class="grid-btns">
    <button class="settings-btn" onclick="sendKey('SETTINGS')"><span>⚙️</span>Settings</button>
    <button class="settings-btn" onclick="sendKey('QUALITY')"><span>🎬</span>Quality</button>
    <button class="settings-btn" onclick="sendKey('SUBTITLES')"><span>💬</span>Captions</button>
    <button class="settings-btn" onclick="sendKey('SPEED')"><span>⚡</span>Speed</button>
    <button class="settings-btn" onclick="sendKey('VOICE_BOOST')"><span>🔊</span>Voice +6dB</button>
    <button class="settings-btn" onclick="sendKey('STATS')"><span>📊</span>Stats</button>
  </div>
  <div class="row-btns" style="margin-top: 6px;">
    <button class="action-btn" onclick="sendKey('NEXT')" style="background:#2d1b1b; border-color:#e53935; color:#ff8a80;">⏭ Next Video</button>
  </div>
</div>

<!-- Fast Video Scrubber (Drag to seek) -->
<div class="card">
  <div class="section-title">Drag Red Line to Seek Faster</div>
  <input type="range" id="seekSlider" min="0" max="100" value="0" style="width:100%; height:8px; accent-color:#ff0000; cursor:pointer;" oninput="onSeekInput(this.value)" onchange="onSeekChange(this.value)"/>
  <div style="display:flex; justify-content:space-between; font-size:12px; color:#aaa; margin-top:6px;">
    <span id="seekTimeText">Drag to jump across video</span>
    <span id="seekPercentText">0%</span>
  </div>
</div>

<!-- D-Pad Navigation Controls -->
<div class="card" style="display:flex; flex-direction:column; align-items:center;">
  <div class="dpad">
    <button class="dpad-btn dpad-up" onclick="sendKey('UP')">▲</button>
    <button class="dpad-btn dpad-left" onclick="sendKey('LEFT')">◀</button>
    <button class="dpad-btn dpad-center" onclick="sendKey('OK')">OK</button>
    <button class="dpad-btn dpad-right" onclick="sendKey('RIGHT')">▶</button>
    <button class="dpad-btn dpad-down" onclick="sendKey('DOWN')">▼</button>
  </div>

  <div class="row-btns">
    <button class="action-btn" onclick="sendKey('BACK')">↩ Back</button>
    <button class="action-btn" onclick="sendKey('LONG_OK')" style="background:#2a2a2a; border-color:#555;">☰ Menu / Hold</button>
    <button class="action-btn" onclick="sendKey('HOME')">⌂ Home</button>
  </div>

  <div class="row-btns" style="margin-top: 10px;">
    <button class="action-btn media-btn" onclick="sendKey('REWIND')">⏪ 10s</button>
    <button class="action-btn media-btn" onclick="sendKey('PLAY_PAUSE')" style="background:#cc0000; color:#fff; font-weight:bold;">⏯ Play/Pause</button>
    <button class="action-btn media-btn" onclick="sendKey('FAST_FORWARD')">⏩ 10s</button>
  </div>

  <div class="row-btns" style="margin-top: 8px;">
    <button class="action-btn" onclick="sendKey('VOLUME_DOWN')">🔉 Vol -</button>
    <button class="action-btn" onclick="sendKey('VOLUME_MUTE')">🔇 Mute</button>
    <button class="action-btn" onclick="sendKey('VOLUME_UP')">🔊 Vol +</button>
  </div>

  <div class="feedback" id="feedback"></div>
</div>

<script>
  function haptic() {
    if (navigator.vibrate) {
      navigator.vibrate(35);
    }
  }

  function onSeekInput(val) {
    document.getElementById('seekPercentText').innerText = val + '%';
    document.getElementById('seekTimeText').innerText = 'Seeking to ' + val + '%';
  }

  function onSeekChange(val) {
    haptic();
    const frac = (parseFloat(val) / 100).toFixed(4);
    fetch('/api/key?code=' + encodeURIComponent('SEEK:' + frac))
      .then(() => showFeedback('Seek: ' + val + '%'))
      .catch(() => showFeedback('Seek failed'));
  }

  function showFeedback(text) {
    const el = document.getElementById('feedback');
    el.innerText = text;
    clearTimeout(window.fbTimer);
    window.fbTimer = setTimeout(() => { el.innerText = ''; }, 1500);
  }

  function sendKey(code) {
    haptic();
    fetch('/api/key?code=' + encodeURIComponent(code))
      .then(res => res.json())
      .then(data => showFeedback(code))
      .catch(err => showFeedback('Error sending key'));
  }

  function sendSearch() {
    const input = document.getElementById('searchInput');
    const q = input.value.trim();
    if (!q) return;
    haptic();
    fetch('/api/search?q=' + encodeURIComponent(q))
      .then(res => res.json())
      .then(data => {
        showFeedback('Sent: ' + q);
        input.value = '';
      })
      .catch(err => showFeedback('Error sending search'));
  }

  document.getElementById('searchInput').addEventListener('keydown', function(e) {
    if (e.key === 'Enter') {
      sendSearch();
    }
  });
</script>
</body>
</html>
        """.trimIndent()
    }
}
