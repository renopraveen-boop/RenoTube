import asyncio
import websockets
import urllib.request
import json
import time

async def run_comparison():
    print("Connecting to Laptop HyperHDR (ws://localhost:8090/json-rpc) and TV RenoTube (http://192.168.1.4:8080/api/ambilight/dump)...")
    async with websockets.connect("ws://localhost:8090/json-rpc") as ws:
        await ws.send(json.dumps({"command": "ledcolors", "subcommand": "ledstream-start", "tan": 1}))
        await ws.recv() # init response
        
        for sample_i in range(12):
            laptop_leds = None
            try:
                msg = await asyncio.wait_for(ws.recv(), timeout=1.0)
                data = json.loads(msg)
                raw = data.get("result", {}).get("leds", [])
                if len(raw) >= 228 * 3:
                    laptop_leds = [(raw[i*3], raw[i*3+1], raw[i*3+2]) for i in range(228)]
            except Exception as e:
                print("Laptop grab error:", e)

            tv_leds = None
            tv_dump = None
            try:
                res = urllib.request.urlopen("http://192.168.1.4:8080/api/ambilight/dump", timeout=1.0)
                tv_dump = json.loads(res.read().decode("utf-8"))
                tv_leds = [tuple(c) for c in tv_dump.get("leds", [])]
            except Exception as e:
                print("TV grab error:", e)

            if laptop_leds and tv_leds:
                l_left = [sum(laptop_leds[i][c] for i in range(0, 40)) // 40 for c in range(3)]
                l_top = [sum(laptop_leds[i][c] for i in range(40, 111)) // 71 for c in range(3)]
                l_right = [sum(laptop_leds[i][c] for i in range(111, 152)) // 41 for c in range(3)]
                l_bottom = [sum(laptop_leds[i][c] for i in range(152, 228)) // 76 for c in range(3)]

                t_left = [sum(tv_leds[i][c] for i in range(0, 40)) // 40 for c in range(3)]
                t_top = [sum(tv_leds[i][c] for i in range(40, 111)) // 71 for c in range(3)]
                t_right = [sum(tv_leds[i][c] for i in range(111, 152)) // 41 for c in range(3)]
                t_bottom = [sum(tv_leds[i][c] for i in range(152, 228)) // 76 for c in range(3)]

                b_y = tv_dump.get("borderY", 0)
                b_x = tv_dump.get("borderX", 0)

                print(f"--- Sample {sample_i+1:02d} ---")
                print(f"  LAPTOP (HyperHDR): Left={l_left} Top={l_top} Right={l_right} Bottom={l_bottom}")
                print(f"  TV (RenoTube):     Left={t_left} Top={t_top} Right={t_right} Bottom={t_bottom}")
                print(f"  TV Active Borders: Top/Bottom Bar: {b_y}%  Left/Right Bar: {b_x}%")
                
                # Check corner LEDs: 0 (Left-Bottom), 39 (Left-Top), 110 (Right-Top), 151 (Right-Bottom)
                print(f"  Corner LEDs (Laptop vs TV):")
                print(f"    LED 00  (L-Bottom): Laptop={laptop_leds[0]}  TV={tv_leds[0]}")
                print(f"    LED 39  (L-Top):    Laptop={laptop_leds[39]}  TV={tv_leds[39]}")
                print(f"    LED 110 (R-Top):    Laptop={laptop_leds[110]}  TV={tv_leds[110]}")
                print(f"    LED 151 (R-Bottom): Laptop={laptop_leds[151]}  TV={tv_leds[151]}")
                print()

            await asyncio.sleep(0.8)

if __name__ == "__main__":
    asyncio.run(run_comparison())
