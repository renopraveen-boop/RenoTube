import asyncio
import websockets
import json
import time
import sys

async def capture_wled(label="Laptop HyperHDR"):
    async with websockets.connect('ws://192.168.1.6/ws') as ws:
        await ws.send('{"lv":true}')
        samples = []
        start = time.time()
        while time.time() - start < 3:
            try:
                msg = await asyncio.wait_for(ws.recv(), timeout=1.0)
                if isinstance(msg, bytes) and len(msg) >= 2 + 228 * 3:
                    data = msg[2:]
                    led_colors = [(data[i*3], data[i*3+1], data[i*3+2]) for i in range(228)]
                    samples.append(led_colors)
            except Exception as e:
                break
        print(f"[{label}] Captured {len(samples)} frames from WLED liveview")
        if samples:
            latest = samples[-1]
            left_avg = [sum(latest[i][c] for i in range(0, 39)) // 39 for c in range(3)]
            top_avg = [sum(latest[i][c] for i in range(39, 110)) // 71 for c in range(3)]
            right_avg = [sum(latest[i][c] for i in range(110, 151)) // 41 for c in range(3)]
            bottom_avg = [sum(latest[i][c] for i in range(151, 227)) // 76 for c in range(3)]
            print(f"[{label}] -> WLED Live Colors:")
            print(f"  Left   (LEDs 0-38):   RGB({left_avg[0]}, {left_avg[1]}, {left_avg[2]})")
            print(f"  Top    (LEDs 39-109): RGB({top_avg[0]}, {top_avg[1]}, {top_avg[2]})")
            print(f"  Right  (LEDs 110-150): RGB({right_avg[0]}, {right_avg[1]}, {right_avg[2]})")
            print(f"  Bottom (LEDs 151-226): RGB({bottom_avg[0]}, {bottom_avg[1]}, {bottom_avg[2]})")
            print(f"  Corner LED 0 (Left-bottom): RGB{latest[0]}")
            print(f"  Corner LED 39 (Top-left):   RGB{latest[39]}")
            print(f"  Corner LED 110 (Top-right):  RGB{latest[110]}")
            print(f"  Corner LED 151 (Bottom-right): RGB{latest[151]}")
            print(f"  Corner LED 227 (Bottom-most left): RGB{latest[227]}")

if __name__ == "__main__":
    lbl = sys.argv[1] if len(sys.argv) > 1 else "Laptop HyperHDR"
    asyncio.run(capture_wled(lbl))
