import ctypes
import time
import json
import urllib.request
import numpy as np

# Load 228 boxes from led_layout.json
with open(r'C:\Users\renop\Downloads\renotube\led_layout.json') as f:
    BOXES = json.load(f)

user32 = ctypes.windll.user32
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
    # BGRA -> RGB
    return arr[:, :, [2, 1, 0]]

def compute_hyperhdr_leds(frame_rgb):
    h, w, _ = frame_rgb.shape
    
    threshold = 13
    y_center, y_33, y_66 = h // 2, h // 3, (h * 2) // 3
    x_center, x_33, x_66 = w // 2, w // 3, (w * 2) // 3
    
    max_y_scan = int(h * 0.35)
    max_x_scan = int(w * 0.35)
    
    non_black_y = 0
    for y in range(max_y_scan):
        if max(frame_rgb[y, x_center]) > threshold or max(frame_rgb[y, x_33]) > threshold or max(frame_rgb[y, x_66]) > threshold:
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

# Find Brave Window
brave_hwnd = None
def enum_cb(hwnd, lparam):
    global brave_hwnd
    length = user32.GetWindowTextLengthW(hwnd)
    if length > 0:
        buff = ctypes.create_unicode_buffer(length + 1)
        user32.GetWindowTextW(hwnd, buff, length + 1)
        if 'Brave' in buff.value:
            brave_hwnd = hwnd
            return False
    return True

user32.EnumWindows(ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)(enum_cb), 0)

def seek_brave_percentage(pct_digit):
    if not brave_hwnd:
        return
    user32.ShowWindow(brave_hwnd, 3)
    user32.SetForegroundWindow(brave_hwnd)
    time.sleep(0.05)
    
    # Send digit key (0x30 = '0', 0x31 = '1', etc.)
    vk = 0x30 + pct_digit
    user32.keybd_event(vk, 0, 0, 0)
    time.sleep(0.04)
    user32.keybd_event(vk, 0, 2, 0)

def seek_tv_ms(ms):
    try:
        urllib.request.urlopen(f'http://192.168.1.4:8080/api/player/seek?ms={ms}', timeout=3.0)
    except Exception as e:
        print(f"Error seeking TV to {ms}ms: {e}")

def get_tv_dump():
    try:
        res = urllib.request.urlopen("http://192.168.1.4:8080/api/ambilight/dump", timeout=2.0)
        tv_dump = json.loads(res.read().decode('utf-8'))
        t_leds = [tuple(c) for c in tv_dump.get('leds', [])]
        t_pos = tv_dump.get('positionMs', 0)
        t_by = tv_dump.get('borderY', 0)
        t_bx = tv_dump.get('borderX', 0)
        return t_leds, t_pos, t_by, t_bx
    except Exception as e:
        print("Error getting TV dump:", e)
        return [(0,0,0)]*228, 0, 0, 0

def main():
    print("==========================================================================")
    print(" EXACT TIMESTAMP-LOCKED COMPARISON: LAPTOP (HyperHDR) vs TV (RenoTube App)")
    print("==========================================================================")
    print("Video: Whalefall | Official Trailer (Duration: 133s / 133,000ms)\n")
    
    # Ensure TV playback is active
    try:
        urllib.request.urlopen('http://192.168.1.4:8080/api/player/play', timeout=2.0)
    except:
        pass
    time.sleep(0.5)

    # Key Scenes: 10% (13.3s), 20% (26.6s), 30% (39.9s), 40% (53.2s), 50% (66.5s), 60% (79.8s), 70% (93.1s), 80% (106.4s)
    scenes = [
        (1, 13300,  "0:13", "Opening Ocean Depths & Title"),
        (2, 26600,  "0:26", "Dark Underwater Abyss & Silhouette"),
        (3, 39900,  "0:40", "Submarine Exterior Glow"),
        (4, 53200,  "0:53", "Cockpit Emergency Amber / Red"),
        (5, 66500,  "1:06", "Deep Sea Crest"),
        (6, 79800,  "1:20", "Fast Cut Highlights"),
        (7, 93100,  "1:33", "Subsurface Flare"),
        (8, 106400, "1:46", "Climactic Ocean Surface")
    ]
    
    results = []
    
    for digit, target_ms, label_time, desc in scenes:
        print(f">>> Synchronizing to EXACT TIMESTAMP [{label_time}] ({desc})...")
        
        # 1. Seek TV to target_ms
        seek_tv_ms(target_ms)
        
        # 2. Seek Brave to percentage digit
        seek_brave_percentage(digit)
        
        # Wait 0.8s for both players to lock onto the frame and settle
        time.sleep(0.8)
        
        # 3. Read TV LED dump
        t_leds, t_pos, t_by, t_bx = get_tv_dump()
        
        # 4. Grab Laptop Frame & calculate HyperHDR LEDs
        l_frame = grab_laptop_frame()
        l_leds, l_by, l_bx = compute_hyperhdr_leds(l_frame)
        
        # 4-Edge Averages: Left (0..39), Top (40..110), Right (111..151), Bottom (152..227)
        l_left = [int(sum(l_leds[i][c] for i in range(0, 40)) / 40) for c in range(3)]
        l_top = [int(sum(l_leds[i][c] for i in range(40, 111)) / 71) for c in range(3)]
        l_right = [int(sum(l_leds[i][c] for i in range(111, 152)) / 41) for c in range(3)]
        l_bottom = [int(sum(l_leds[i][c] for i in range(152, 228)) / 76) for c in range(3)]
        
        t_left = [int(sum(t_leds[i][c] for i in range(0, 40)) / 40) for c in range(3)]
        t_top = [int(sum(t_leds[i][c] for i in range(40, 111)) / 71) for c in range(3)]
        t_right = [int(sum(t_leds[i][c] for i in range(111, 152)) / 41) for c in range(3)]
        t_bottom = [int(sum(t_leds[i][c] for i in range(152, 228)) / 76) for c in range(3)]
        
        diff_left = [abs(t_left[c] - l_left[c]) for c in range(3)]
        diff_top = [abs(t_top[c] - l_top[c]) for c in range(3)]
        diff_right = [abs(t_right[c] - l_right[c]) for c in range(3)]
        diff_bottom = [abs(t_bottom[c] - l_bottom[c]) for c in range(3)]
        
        print(f"  Timestamp: [{label_time}] (Target: {target_ms}ms, TV Pos: {t_pos}ms)")
        print(f"  Laptop HyperHDR : Left={l_left} Top={l_top} Right={l_right} Bottom={l_bottom}")
        print(f"  TV RenoTube     : Left={t_left} Top={t_top} Right={t_right} Bottom={t_bottom}")
        print(f"  Delta RGB (Diff): Left={diff_left} Top={diff_top} Right={diff_right} Bottom={diff_bottom}\n")
        
        results.append({
            'timestamp': label_time,
            'desc': desc,
            'target_ms': target_ms,
            'tv_pos_ms': t_pos,
            'laptop': {'left': l_left, 'top': l_top, 'right': l_right, 'bottom': l_bottom},
            'tv': {'left': t_left, 'top': t_top, 'right': t_right, 'bottom': t_bottom},
            'diff': {'left': diff_left, 'top': diff_top, 'right': diff_right, 'bottom': diff_bottom}
        })
        
        time.sleep(0.5)
        
    with open(r'C:\Users\renop\.gemini\antigravity\brain\dfe7dee6-382c-4a33-958a-957356d74f51\timestamp_comparison.json', 'w') as out:
        json.dump(results, out, indent=2)
    print("All timestamp-locked comparisons saved successfully to timestamp_comparison.json!")

if __name__ == '__main__':
    main()
