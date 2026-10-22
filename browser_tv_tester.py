import http.server
import socketserver
import subprocess
import threading
import time
import json
import urllib.parse
import os
import sys

TV_IP = "192.168.1.4:5555"
PORT = 8500
SCRCPY_PATH = r"C:\Users\renop\AppData\Local\Microsoft\WinGet\Packages\Genymobile.scrcpy_Microsoft.Winget.Source_8wekyb3d8bbwe\scrcpy-win64-v4.1\scrcpy.exe"

current_frame = b""
frame_lock = threading.Lock()
frame_timestamp = 0

def ensure_adb():
    try:
        subprocess.run(["adb", "connect", TV_IP], capture_output=True, timeout=5)
    except Exception as e:
        print(f"ADB connect error: {e}")

def capture_screen_worker():
    global current_frame, frame_timestamp
    ensure_adb()
    while True:
        try:
            p = subprocess.run(["adb", "-s", TV_IP, "exec-out", "screencap", "-p"], capture_output=True, timeout=5)
            if p.returncode == 0 and len(p.stdout) > 2000:
                with frame_lock:
                    current_frame = p.stdout
                    frame_timestamp = time.time()
            time.sleep(0.5)
        except Exception as e:
            ensure_adb()
            time.sleep(1.0)

def send_key(key_code):
    ensure_adb()
    try:
        subprocess.run(["adb", "-s", TV_IP, "shell", "input", "keyevent", str(key_code)], timeout=3)
        # Immediate follow-up screencap for instant visual feedback
        threading.Thread(target=refresh_single_frame, daemon=True).start()
    except Exception as e:
        print(f"Error sending key {key_code}: {e}")

def send_text(text):
    ensure_adb()
    try:
        escaped = urllib.parse.quote(text).replace("%20", "%s")
        subprocess.run(["adb", "-s", TV_IP, "shell", "input", "text", escaped], timeout=3)
        threading.Thread(target=refresh_single_frame, daemon=True).start()
    except Exception as e:
        print(f"Error sending text {text}: {e}")

def refresh_single_frame():
    global current_frame, frame_timestamp
    time.sleep(0.12)
    try:
        p = subprocess.run(["adb", "-s", TV_IP, "exec-out", "screencap", "-p"], capture_output=True, timeout=5)
        if p.returncode == 0 and len(p.stdout) > 2000:
            with frame_lock:
                current_frame = p.stdout
                frame_timestamp = time.time()
    except Exception:
        pass

HTML_PAGE = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>RenoTube TV - Live Browser Tester</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            background: #0f0f0f;
            color: #f1f1f1;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            display: flex;
            flex-direction: column;
            height: 100vh;
            overflow: hidden;
        }
        header {
            background: #181818;
            padding: 12px 24px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            border-bottom: 1px solid #282828;
        }
        .logo {
            display: flex;
            align-items: center;
            gap: 10px;
            font-weight: 700;
            font-size: 18px;
        }
        .logo-badge {
            background: #ff0000;
            color: white;
            padding: 3px 8px;
            border-radius: 4px;
            font-size: 12px;
            font-weight: 900;
        }
        .header-actions {
            display: flex;
            gap: 12px;
            align-items: center;
        }
        .btn {
            background: #272727;
            color: #fff;
            border: 1px solid #3f3f3f;
            padding: 8px 16px;
            border-radius: 18px;
            cursor: pointer;
            font-size: 13px;
            font-weight: 500;
            transition: all 0.15s ease;
            text-decoration: none;
            display: inline-flex;
            align-items: center;
            gap: 6px;
        }
        .btn:hover { background: #3f3f3f; }
        .btn-primary { background: #ff0000; border-color: #ff0000; }
        .btn-primary:hover { background: #cc0000; }
        .btn-gold { background: #2a2200; border-color: #ffb300; color: #ffb300; }
        .btn-gold:hover { background: #3a2e00; }

        .main-container {
            display: flex;
            flex: 1;
            overflow: hidden;
        }
        .screen-pane {
            flex: 1;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 16px;
            position: relative;
            background: #000;
        }
        .screen-wrapper {
            position: relative;
            width: 100%;
            height: 100%;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        #tv-screen {
            max-width: 100%;
            max-height: 100%;
            aspect-ratio: 16 / 9;
            object-fit: contain;
            border-radius: 8px;
            box-shadow: 0 8px 24px rgba(0,0,0,0.8);
            border: 1px solid #222;
        }
        .status-pill {
            position: absolute;
            top: 24px;
            left: 24px;
            background: rgba(0,0,0,0.7);
            backdrop-filter: blur(8px);
            padding: 6px 12px;
            border-radius: 20px;
            font-size: 12px;
            display: flex;
            align-items: center;
            gap: 8px;
            border: 1px solid rgba(255,255,255,0.1);
        }
        .dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: #00e676;
            animation: pulse 2s infinite;
        }
        @keyframes pulse { 0% { opacity: 1; } 50% { opacity: 0.4; } 100% { opacity: 1; } }

        .sidebar-controls {
            width: 340px;
            background: #141414;
            border-left: 1px solid #242424;
            display: flex;
            flex-direction: column;
            overflow-y: auto;
            padding: 20px;
            gap: 20px;
        }
        .panel-section {
            background: #1c1c1c;
            border-radius: 12px;
            padding: 16px;
            border: 1px solid #2a2a2a;
        }
        .panel-title {
            font-size: 12px;
            text-transform: uppercase;
            letter-spacing: 1px;
            color: #888;
            margin-bottom: 12px;
            font-weight: 700;
        }

        /* DPAD Cross */
        .dpad-container {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 8px;
            width: 210px;
            margin: 0 auto;
        }
        .dpad-btn {
            background: #282828;
            color: #fff;
            border: 1px solid #383838;
            border-radius: 8px;
            height: 52px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 16px;
            font-weight: bold;
            cursor: pointer;
            user-select: none;
            transition: all 0.1s ease;
        }
        .dpad-btn:hover { background: #3a3a3a; }
        .dpad-btn:active { background: #ff0000; transform: scale(0.95); }
        .dpad-center {
            background: #ff0000;
            border-color: #ff0000;
            font-size: 14px;
        }
        .dpad-center:hover { background: #cc0000; }

        .action-row {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 8px;
            margin-top: 12px;
        }
        .nav-btn {
            background: #222;
            color: #eee;
            border: 1px solid #333;
            border-radius: 8px;
            padding: 10px;
            cursor: pointer;
            font-size: 13px;
            font-weight: 500;
            text-align: center;
        }
        .nav-btn:hover { background: #333; }
        .nav-btn:active { background: #555; }

        .keyboard-hint {
            font-size: 11px;
            color: #777;
            margin-top: 8px;
            line-height: 1.5;
            background: #111;
            padding: 8px 12px;
            border-radius: 6px;
            border-left: 3px solid #ff0000;
        }
        kbd {
            background: #252525;
            border: 1px solid #444;
            border-radius: 4px;
            padding: 2px 5px;
            font-size: 10px;
            color: #ddd;
        }

        .input-group {
            display: flex;
            gap: 8px;
            margin-top: 6px;
        }
        .text-input {
            flex: 1;
            background: #111;
            border: 1px solid #333;
            border-radius: 6px;
            padding: 8px 12px;
            color: #fff;
            font-size: 13px;
            outline: none;
        }
        .text-input:focus { border-color: #ff0000; }
    </style>
</head>
<body>
    <header>
        <div class="logo">
            <span class="logo-badge">TV</span>
            <span>RenoTube Live Browser Tester</span>
        </div>
        <div class="header-actions">
            <button class="btn btn-gold" onclick="launchScrcpy()">⚡ Launch 60fps Native Mirror</button>
            <a href="http://192.168.1.4:8080" target="_blank" class="btn">📱 Mobile Remote</a>
        </div>
    </header>

    <div class="main-container">
        <div class="screen-pane" id="screenPane" tabindex="0">
            <div class="status-pill">
                <div class="dot"></div>
                <span id="status-text">Connected to Sony TV (192.168.1.4)</span>
            </div>
            <div class="screen-wrapper">
                <img id="tv-screen" src="/screen.jpg" alt="RenoTube TV Screen" />
            </div>
        </div>

        <div class="sidebar-controls">
            <!-- DPAD Navigation Section -->
            <div class="panel-section">
                <div class="panel-title">D-Pad Remote Controls</div>
                <div class="dpad-container">
                    <div></div>
                    <button class="dpad-btn" onclick="sendKey(19)" title="DPAD UP (Arrow Up)">▲</button>
                    <div></div>
                    <button class="dpad-btn" onclick="sendKey(21)" title="DPAD LEFT (Arrow Left)">◀</button>
                    <button class="dpad-btn dpad-center" onclick="sendKey(23)" title="OK / SELECT (Enter)">OK</button>
                    <button class="dpad-btn" onclick="sendKey(22)" title="DPAD RIGHT (Arrow Right)">▶</button>
                    <div></div>
                    <button class="dpad-btn" onclick="sendKey(20)" title="DPAD DOWN (Arrow Down)">▼</button>
                    <div></div>
                </div>

                <div class="action-row">
                    <button class="nav-btn" onclick="sendKey(4)">↩ Back (Esc)</button>
                    <button class="nav-btn" onclick="sendKey(85)">⏯ Play / Pause</button>
                    <button class="nav-btn" onclick="sendKey(3)">⌂ Home</button>
                    <button class="nav-btn" onclick="sendKey(84)">🔍 Search</button>
                </div>

                <div class="keyboard-hint">
                    <strong>Keyboard Controls:</strong> Use <kbd>↑</kbd> <kbd>↓</kbd> <kbd>←</kbd> <kbd>→</kbd> arrow keys to navigate, <kbd>Enter</kbd> to select, <kbd>Esc</kbd> / <kbd>Backspace</kbd> to go back, and <kbd>Space</kbd> to pause.
                </div>
            </div>

            <!-- Fast Quick Actions -->
            <div class="panel-section">
                <div class="panel-title">Play Video by ID</div>
                <div class="input-group">
                    <input type="text" id="videoIdInput" class="text-input" placeholder="e.g. yu831suN6xI" value="yu831suN6xI" />
                    <button class="btn btn-primary" onclick="playVideo()">Play</button>
                </div>
            </div>

            <!-- Send Text to Search -->
            <div class="panel-section">
                <div class="panel-title">Type & Search</div>
                <div class="input-group">
                    <input type="text" id="searchInput" class="text-input" placeholder="Search keywords..." onkeydown="if(event.key==='Enter') sendSearch()" />
                    <button class="btn" onclick="sendSearch()">Type</button>
                </div>
            </div>

            <!-- Live Telemetry -->
            <div class="panel-section">
                <div class="panel-title">Playback Status</div>
                <div id="player-telemetry" style="font-size: 12px; color: #aaa; line-height: 1.6;">
                    Loading status...
                </div>
            </div>
        </div>
    </div>

    <script>
        const img = document.getElementById('tv-screen');
        const telemetry = document.getElementById('player-telemetry');
        let isRefreshing = false;

        function refreshImage() {
            if (isRefreshing) return;
            isRefreshing = true;
            const newImg = new Image();
            newImg.onload = () => {
                img.src = newImg.src;
                isRefreshing = false;
            };
            newImg.onerror = () => { isRefreshing = false; };
            newImg.src = '/screen.jpg?t=' + Date.now();
        }

        // Auto refresh screen every 600ms
        setInterval(refreshImage, 600);

        function sendKey(code) {
            fetch('/api/key?code=' + code)
                .then(() => setTimeout(refreshImage, 150))
                .catch(console.error);
        }

        function playVideo() {
            const id = document.getElementById('videoIdInput').value.trim();
            if (!id) return;
            fetch('http://192.168.1.4:8080/api/play?videoId=' + encodeURIComponent(id))
                .then(() => setTimeout(refreshImage, 800))
                .catch(console.error);
        }

        function sendSearch() {
            const text = document.getElementById('searchInput').value.trim();
            if (!text) return;
            fetch('/api/text?text=' + encodeURIComponent(text))
                .then(() => {
                    document.getElementById('searchInput').value = '';
                    setTimeout(refreshImage, 200);
                });
        }

        function launchScrcpy() {
            fetch('/api/scrcpy').then(r => r.json()).then(d => {
                alert(d.message || "Launched 60fps Native Mirror!");
            });
        }

        // Keyboard navigation
        window.addEventListener('keydown', (e) => {
            if (document.activeElement && (document.activeElement.tagName === 'INPUT')) {
                return; // Let typing inside inputs work normally
            }
            switch (e.key) {
                case 'ArrowUp': sendKey(19); e.preventDefault(); break;
                case 'ArrowDown': sendKey(20); e.preventDefault(); break;
                case 'ArrowLeft': sendKey(21); e.preventDefault(); break;
                case 'ArrowRight': sendKey(22); e.preventDefault(); break;
                case 'Enter': sendKey(23); e.preventDefault(); break;
                case 'Escape':
                case 'Backspace': sendKey(4); e.preventDefault(); break;
                case ' ': sendKey(85); e.preventDefault(); break;
            }
        });

        // Polling telemetry
        setInterval(() => {
            fetch('http://192.168.1.4:8080/api/player/status')
                .then(r => r.json())
                .then(data => {
                    if (data.active) {
                        const curSec = Math.floor(data.positionMs / 1000);
                        const durSec = Math.floor(data.durationMs / 1000);
                        telemetry.innerHTML = `
                            <div><strong>State:</strong> <span style="color:#00e676">Playing</span></div>
                            <div><strong>Video ID:</strong> ${data.videoId}</div>
                            <div><strong>Time:</strong> ${curSec}s / ${durSec}s</div>
                        `;
                    } else {
                        telemetry.innerHTML = `<div><strong>State:</strong> Browsing Feed / Idle</div>`;
                    }
                })
                .catch(() => {
                    telemetry.innerHTML = `<div>Connecting to TV...</div>`;
                });
        }, 1500);
    </script>
</body>
</html>
"""

class RequestHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        params = urllib.parse.parse_qs(parsed.query)

        if path == "/" or path == "/index.html":
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            self.wfile.write(HTML_PAGE.encode("utf-8"))

        elif path == "/screen.jpg" or path == "/screen.png":
            with frame_lock:
                frame_data = current_frame
            if frame_data:
                self.send_response(200)
                self.send_header("Content-Type", "image/png")
                self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
                self.end_headers()
                self.wfile.write(frame_data)
            else:
                self.send_response(404)
                self.end_headers()

        elif path == "/api/key":
            code = params.get("code", ["0"])[0]
            send_key(code)
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"status":"ok"}')

        elif path == "/api/text":
            text = params.get("text", [""])[0]
            send_text(text)
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"status":"ok"}')

        elif path == "/api/scrcpy":
            def run_scrcpy():
                subprocess.run([SCRCPY_PATH, "-s", TV_IP, "--window-title", "RenoTube TV - 60fps Native Mirror"])
            threading.Thread(target=run_scrcpy, daemon=True).start()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"status":"ok","message":"Launched 60fps native window on your desktop!"}')

        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        pass # Suppress noisy request logs

class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True

def main():
    threading.Thread(target=capture_screen_worker, daemon=True).start()
    server = ThreadedHTTPServer(("0.0.0.0", PORT), RequestHandler)
    print(f"RenoTube Live Browser Tester running at http://localhost:{PORT}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass

if __name__ == "__main__":
    main()
