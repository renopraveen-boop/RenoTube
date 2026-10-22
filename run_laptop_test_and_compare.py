import ctypes
import time
import json
import numpy as np
import sys

# Load 228 boxes
with open(r'C:\Users\renop\Downloads\renotube\led_layout.json') as f:
    BOXES = json.load(f)

TV_DATA_PATH = r'C:\Users\renop\.gemini\antigravity\brain\dfe7dee6-382c-4a33-958a-957356d74f51\tv_full_recording.json'
LAPTOP_DATA_PATH = r'C:\Users\renop\.gemini\antigravity\brain\dfe7dee6-382c-4a33-958a-957356d74f51\laptop_full_recording.json'
REPORT_PATH = r'C:\Users\renop\.gemini\antigravity\brain\dfe7dee6-382c-4a33-958a-957356d74f51\full_trailer_comparison_report.json'

user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32
gdi32 = ctypes.windll.gdi32

hwinsta = user32.OpenWindowStationW('winsta0', False, 0x0000037F)
if hwinsta: user32.SetProcessWindowStation(hwinsta)
hdesk = user32.OpenDesktopW('default', 0, False, 0x000001FF)
if hdesk: user32.SetThreadDesktop(hdesk)

user32.SetProcessDPIAware()
screen_w = user32.GetSystemMetrics(0)
screen_h = user32.GetSystemMetrics(1)

hdc_screen = user32.GetDC(0)
hdc_mem = gdi32.CreateCompatibleDC(hdc_screen)
hbm = gdi32.CreateCompatibleBitmap(hdc_screen, screen_w, screen_h)
gdi32.SelectObject(hdc_mem, hbm)

class BITMAPINFOHEADER(ctypes.Structure):
    _fields_ = [
        ('biSize', ctypes.c_uint32),
        ('biWidth', ctypes.c_int32),
        ('biHeight', ctypes.c_int32),
        ('biPlanes', ctypes.c_uint16),
        ('biBitCount', ctypes.c_uint16),
        ('biCompression', ctypes.c_uint32),
        ('biSizeImage', ctypes.c_uint32),
        ('biXPelsPerMeter', ctypes.c_int32),
        ('biYPelsPerMeter', ctypes.c_int32),
        ('biClrUsed', ctypes.c_uint32),
        ('biClrImportant', ctypes.c_uint32)
    ]

bmi = BITMAPINFOHEADER()
bmi.biSize = ctypes.sizeof(BITMAPINFOHEADER)
bmi.biWidth = screen_w
bmi.biHeight = -screen_h
bmi.biPlanes = 1
bmi.biBitCount = 32
bmi.biCompression = 0

buf = ctypes.create_string_buffer(screen_w * screen_h * 4)

def grab_laptop_frame():
    gdi32.BitBlt(hdc_mem, 0, 0, screen_w, screen_h, hdc_screen, 0, 0, 0x00CC0020)
    gdi32.GetDIBits(hdc_mem, hbm, 0, screen_h, buf, ctypes.byref(bmi), 0)
    arr = np.frombuffer(buf, dtype=np.uint8).reshape((screen_h, screen_w, 4))
    return arr[:, :, [2, 1, 0]]

def grab_center_luma():
    gdi32.BitBlt(hdc_mem, 0, 0, screen_w, screen_h, hdc_screen, 0, 0, 0x00CC0020)
    gdi32.GetDIBits(hdc_mem, hbm, 0, screen_h, buf, ctypes.byref(bmi), 0)
    return np.frombuffer(buf, dtype=np.uint8).reshape((screen_h, screen_w, 4))[400:600, 800:1000, 0].mean()

def compute_hyperhdr_leds(frame_rgb):
    h, w, _ = frame_rgb.shape
    
    threshold = 13
    y_center, y_33, y_66 = h // 2, h // 3, (h * 2) // 3
    x_center, x_33, x_66 = w // 2, w // 3, (w * 2) // 3
    
    max_y_scan = int(h * 0.35)
    max_x_scan = int(w * 0.35)
    
    non_black_y = 0
    for y in range(max_y_scan):
        if max(frame_rgb[y, x_center]) > threshold or max(frame_rgb[y, y_33]) > threshold or max(frame_rgb[y, y_66]) > threshold:
            non_black_y = y
            break
            
    non_black_x = 0
    for x in range(max_x_scan):
        if max(frame_rgb[y_center, x]) > threshold or max(frame_rgb[y_33, x]) > threshold or max(frame_rgb[y_66, x]) > threshold:
            non_black_x = x
            break

    act_w = max(2, w - 2 * non_black_x)
    act_h = max(2, h - 2 * non_black_y)
    
    led_colors = []
    for box in BOXES:
        hmin = non_black_x + int(act_w * box['hmin'])
        hmax = max(hmin + 1, non_black_x + int(act_w * box['hmax']))
        vmin = non_black_y + int(act_h * box['vmin'])
        vmax = max(vmin + 1, non_black_y + int(act_h * box['vmax']))
        
        region = frame_rgb[vmin:vmax, hmin:hmax]
        if region.size > 0:
            avg = region.mean(axis=(0, 1))
            # HyperHDR Optical Gamma 1.6
            r = int(pow(avg[0] / 255.0, 1.6) * 255.0)
            g = int(pow(avg[1] / 255.0, 1.6) * 255.0)
            b = int(pow(avg[2] / 255.0, 1.6) * 255.0)
            led_colors.append((r, g, b))
        else:
            led_colors.append((0, 0, 0))
            
    return led_colors, non_black_y, non_black_x

def prepare_and_start_playback():
    brave_hwnd = None
    def enum_cb(hwnd, lparam):
        nonlocal brave_hwnd
        length = user32.GetWindowTextLengthW(hwnd)
        if length > 0 and user32.IsWindowVisible(hwnd):
            buff = ctypes.create_unicode_buffer(length + 1)
            user32.GetWindowTextW(hwnd, buff, length + 1)
            if 'Brave' in buff.value or 'Whalefall' in buff.value:
                brave_hwnd = hwnd
                return False
        return True
    user32.EnumWindows(ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)(enum_cb), 0)
    
    if not brave_hwnd:
        print("ERROR: Could not find Brave window!")
        return False
        
    cur_t = kernel32.GetCurrentThreadId()
    win_t = user32.GetWindowThreadProcessId(brave_hwnd, None)
    user32.AttachThreadInput(cur_t, win_t, True)
    user32.ShowWindow(brave_hwnd, 3)
    user32.SetForegroundWindow(brave_hwnd)
    user32.SetFocus(brave_hwnd)
    user32.AttachThreadInput(cur_t, win_t, False)
    time.sleep(0.1)
    
    # Check if playing
    c1 = grab_center_luma()
    time.sleep(0.4)
    c2 = grab_center_luma()
    if abs(c1 - c2) < 0.2:
        print("Video was paused, pressing 'k' to start...")
        user32.keybd_event(0x4B, 0, 0, 0)
        time.sleep(0.04)
        user32.keybd_event(0x4B, 0, 2, 0)
        time.sleep(0.5)
        
    # Press '0' to restart from 0:00
    user32.keybd_event(0x30, 0, 0, 0)
    time.sleep(0.04)
    user32.keybd_event(0x30, 0, 2, 0)
    time.sleep(0.3)
    
    # Hide mouse cursor in corner so UI hides
    user32.SetCursorPos(0, 1070)
    time.sleep(0.2)
    return True

def main():
    print("==========================================================================")
    print(" LAPTOP FULL-VIDEO RECORDER & SIDE-BY-SIDE AMBILIGHT COMPARISON")
    print("==========================================================================")
    print("Video: Whalefall | Official Trailer (Duration: ~133s)")
    print("Mode: Pure Clean Fullscreen (1920x1080) at 4 Hz\n")
    
    if not prepare_and_start_playback():
        print("Failed to initialize Brave playback. Exiting.")
        return
        
    print("Video rolling from 0:00 in pure clean fullscreen! Recording started...\n")
    records = []
    start_time = time.time()
    last_print = 0
    
    # 133 seconds total duration
    while True:
        elapsed = time.time() - start_time
        if elapsed > 133.5:
            break
            
        loop_t0 = time.time()
        
        # Grab frame & compute HyperHDR leds
        frame = grab_laptop_frame()
        leds, by, bx = compute_hyperhdr_leds(frame)
        
        # 4-Edge Averages
        left_mid = [int(sum(leds[i][c] for i in range(0, 40)) / 40) for c in range(3)]
        top_mid = [int(sum(leds[i][c] for i in range(40, 111)) / 71) for c in range(3)]
        right_mid = [int(sum(leds[i][c] for i in range(111, 152)) / 41) for c in range(3)]
        bottom_mid = [int(sum(leds[i][c] for i in range(152, 228)) / 76) for c in range(3)]
        
        records.append({
            'pos_sec': round(elapsed, 2),
            'pos_ms': int(elapsed * 1000),
            'borderY': by,
            'borderX': bx,
            'left': left_mid,
            'top': top_mid,
            'right': right_mid,
            'bottom': bottom_mid,
            'leds': leds
        })
        
        if elapsed - last_print >= 5.0:
            last_print = elapsed
            cur_min = int(elapsed // 60)
            cur_sec = int(elapsed % 60)
            pct = (elapsed / 133.0) * 100.0
            print(f"  [{cur_min:02d}:{cur_sec:02d} / 02:13] ({pct:5.1f}%) | Captured {len(records)} frames | Mid: L={left_mid} T={top_mid} R={right_mid}")
            sys.stdout.flush()
            
        dt = time.time() - loop_t0
        sleep_dur = max(0.01, 0.25 - dt)
        time.sleep(sleep_dur)
        
    print(f"\nLaptop recording complete! Total frames captured: {len(records)}")
    with open(LAPTOP_DATA_PATH, 'w') as f:
        json.dump(records, f)
    print(f"Saved laptop dataset to {LAPTOP_DATA_PATH}\n")
    
    # Load TV dataset
    print("Loading TV dataset for side-by-side timestamp-locked comparison...")
    try:
        with open(TV_DATA_PATH) as f:
            tv_records = json.load(f)
    except Exception as e:
        print("Error loading TV dataset:", e)
        return
        
    print(f"TV dataset loaded: {len(tv_records)} frames\n")
    
    # Checkpoints
    checkpoints = [5, 10, 15, 20, 25, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130]
    comparison_results = []
    
    print("========================================================================================================")
    print(" COMPLETE FULL-LENGTH TIMESTAMP-BY-TIMESTAMP AMBILIGHT COMPARISON (TV vs LAPTOP)")
    print("========================================================================================================")
    header = f"{'Time':<7} | {'Edge':<6} | {'Laptop (HyperHDR)':<16} | {'TV (RenoTube)':<16} | {'Delta RGB':<12} | {'Parity'}"
    print(header)
    print("-" * len(header))
    
    def find_closest(dataset, target_sec):
        best = None
        best_diff = 999
        for r in dataset:
            d = abs(r['pos_sec'] - target_sec)
            if d < best_diff:
                best_diff = d
                best = r
        return best
        
    for cp in checkpoints:
        l_pt = find_closest(records, cp)
        t_pt = find_closest(tv_records, cp)
        if not l_pt or not t_pt:
            continue
            
        cp_min = cp // 60
        cp_sec = cp % 60
        time_str = f"{cp_min:02d}:{cp_sec:02d}"
        
        edges = ['left', 'top', 'right', 'bottom']
        edge_names = ['Left', 'Top', 'Right', 'Bottom']
        
        cp_diffs = {}
        for edge_k, edge_n in zip(edges, edge_names):
            l_col = l_pt[edge_k]
            t_col = t_pt[edge_k]
            diff = [abs(t_col[c] - l_col[c]) for c in range(3)]
            cp_diffs[edge_k] = diff
            
            # Parity verdict
            max_d = max(diff)
            if max_d <= 6:
                verdict = "EXACT MATCH (<=6)"
            elif max_d <= 15:
                verdict = "CLOSE (<15)"
            else:
                verdict = "DIVERGENT"
                
            print(f"{time_str:<7} | {edge_n:<6} | {str(l_col):<16} | {str(t_col):<16} | {str(diff):<12} | {verdict}")
        print("-" * len(header))
        
        comparison_results.append({
            'checkpoint_sec': cp,
            'time_str': time_str,
            'laptop': {e: l_pt[e] for e in edges},
            'tv': {e: t_pt[e] for e in edges},
            'diff': cp_diffs
        })
        
    with open(REPORT_PATH, 'w') as f:
        json.dump(comparison_results, f, indent=2)
    print(f"\nSaved comprehensive comparison report to {REPORT_PATH}")

if __name__ == '__main__':
    main()
