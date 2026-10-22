import urllib.request
import time
import json
import subprocess
import sys

OUTPUT_FILE = r'C:\Users\renop\.gemini\antigravity\brain\dfe7dee6-382c-4a33-958a-957356d74f51\tv_full_recording.json'

def main():
    print("==========================================================================")
    print(" TV FULL-VIDEO AMBILIGHT RECORDER")
    print("==========================================================================")
    print("Video: Whalefall | Official Trailer (rN9F6-tbySY)")
    print("Duration: ~133 seconds\n")
    
    # 1. Reset/Seek to 0ms
    try:
        urllib.request.urlopen("http://192.168.1.4:8080/api/player/seek?ms=0", timeout=3.0)
        time.sleep(1.0)
        urllib.request.urlopen("http://192.168.1.4:8080/api/player/play", timeout=3.0)
    except Exception as e:
        print("Initial seek/play error:", e)
        
    records = []
    start_wall_time = time.time()
    last_print_time = 0
    consecutive_errors = 0
    
    print("Recording live Ambilight telemetry from TV...")
    
    while True:
        loop_start = time.time()
        
        try:
            # Poll status and dump
            status_res = urllib.request.urlopen("http://192.168.1.4:8080/api/player/status", timeout=1.5)
            status_data = json.loads(status_res.read().decode('utf-8'))
            
            dump_res = urllib.request.urlopen("http://192.168.1.4:8080/api/ambilight/dump", timeout=1.5)
            dump_data = json.loads(dump_res.read().decode('utf-8'))
            
            consecutive_errors = 0
        except Exception as e:
            consecutive_errors += 1
            if consecutive_errors > 10:
                print(f"\nStopped: TV connection lost ({e})")
                break
            time.sleep(0.2)
            continue
            
        vid = status_data.get('videoId', '')
        pos_ms = status_data.get('positionMs', dump_data.get('positionMs', 0))
        dur_ms = status_data.get('durationMs', 133000)
        is_playing = status_data.get('isPlaying', False)
        
        # Stop condition: video finished or changed to next video
        if vid != 'rN9F6-tbySY' and len(records) > 50:
            print(f"\nVideo transitioned to '{vid}' - recording complete!")
            break
            
        if pos_ms >= 132000 and len(records) > 50:
            print(f"\nReached end of video ({pos_ms}ms / {dur_ms}ms) - recording complete!")
            break
            
        # Store frame telemetry
        records.append({
            'wall_sec': round(time.time() - start_wall_time, 3),
            'pos_ms': pos_ms,
            'pos_sec': round(pos_ms / 1000.0, 2),
            'borderY': dump_data.get('borderY', 0),
            'borderX': dump_data.get('borderX', 0),
            'left': dump_data.get('left', {}).get('mid', [0, 0, 0]),
            'top': dump_data.get('top', {}).get('mid', [0, 0, 0]),
            'right': dump_data.get('right', {}).get('mid', [0, 0, 0]),
            'bottom': dump_data.get('bottom', {}).get('mid', [0, 0, 0]),
            'leds': dump_data.get('leds', [])
        })
        
        # Print progress every 5 seconds
        now = time.time()
        if now - last_print_time >= 5.0:
            last_print_time = now
            cur_sec = pos_ms / 1000.0
            total_sec = dur_ms / 1000.0 if dur_ms > 0 else 133.0
            pct = (cur_sec / total_sec) * 100.0 if total_sec > 0 else 0
            cur_min = int(cur_sec // 60)
            cur_rem_sec = int(cur_sec % 60)
            tot_min = int(total_sec // 60)
            tot_rem_sec = int(total_sec % 60)
            print(f"  [{cur_min:02d}:{cur_rem_sec:02d} / {tot_min:02d}:{tot_rem_sec:02d}] ({pct:5.1f}%) | Captured {len(records)} frames | Edge Mid: L={dump_data.get('left',{}).get('mid')} T={dump_data.get('top',{}).get('mid')} R={dump_data.get('right',{}).get('mid')}")
            sys.stdout.flush()
            
        elapsed = time.time() - loop_start
        sleep_time = max(0.01, 0.25 - elapsed)
        time.sleep(sleep_time)
        
    print(f"\nTotal frames captured: {len(records)}")
    
    # Save recording to file
    with open(OUTPUT_FILE, 'w') as f:
        json.dump(records, f)
    print(f"Successfully saved TV recording to {OUTPUT_FILE}")
    
    # Force stop RenoTube on the TV to release WLED completely
    print("\nForce-stopping RenoTube on TV to release WLED connection...")
    try:
        subprocess.run(['adb', '-s', '192.168.1.4:5555', 'shell', 'am', 'force-stop', 'com.renotube.tv'], check=True)
        print("RenoTube app stopped on TV successfully!")
    except Exception as e:
        print("Error force-stopping app on TV:", e)

if __name__ == '__main__':
    main()
