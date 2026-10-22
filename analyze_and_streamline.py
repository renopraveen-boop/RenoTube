import ctypes
import time
import json
import urllib.request
from PIL import Image
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
    
    # 1. HyperHDR Black Border Detection (5% threshold = 13/255)
    # Check 3 lines: 33%, 50%, 66%
    threshold = 13
    y_center, y_33, y_66 = h // 2, h // 3, (h * 2) // 3
    x_center, x_33, x_66 = w // 2, w // 3, (w * 2) // 3
    
    max_y_scan = int(h * 0.35)
    max_x_scan = int(w * 0.35)
    
    non_black_y = 0
    for y in range(max_y_scan):
        c1 = frame_rgb[y, x_center]
        c2 = frame_rgb[y, x_33]
        c3 = frame_rgb[y, x_66]
        if max(c1) > threshold or max(c2) > threshold or max(c3) > threshold:
            non_black_y = y
            break
            
    non_black_x = 0
    for x in range(max_x_scan):
        c1 = frame_rgb[y_center, x]
        c2 = frame_rgb[y_33, x]
        c3 = frame_rgb[y_66, x]
        if max(c1) > threshold or max(c2) > threshold or max(c3) > threshold:
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
            # HyperHDR Optical Gamma 1.5
            r = int(pow(avg[0] / 255.0, 1.5) * 255.0)
            g = int(pow(avg[1] / 255.0, 1.5) * 255.0)
            b = int(pow(avg[2] / 255.0, 1.5) * 255.0)
            led_colors.append((r, g, b))
        else:
            led_colors.append((0, 0, 0))
            
    return led_colors, non_black_y, non_black_x

def main():
    print("==========================================================================")
    print(" LIVE AMBILIGHT ANALYSIS: LAPTOP (HyperHDR Standard) vs TV (RenoTube App)")
    print("==========================================================================")
    print("Analyzing Whalefall Movie Trailer across 15 real-time samples...\n")
    
    comparisons = []
    
    for sample_i in range(15):
        # 1. Grab Laptop frame & compute HyperHDR optical color standard
        t0 = time.time()
        laptop_frame = grab_laptop_frame()
        l_leds, l_by, l_bx = compute_hyperhdr_leds(laptop_frame)
        
        # 2. Grab TV Ambilight dump
        try:
            res = urllib.request.urlopen("http://192.168.1.4:8080/api/ambilight/dump", timeout=1.0)
            tv_dump = json.loads(res.read().decode('utf-8'))
            t_leds = [tuple(c) for c in tv_dump.get('leds', [])]
            t_by = tv_dump.get('borderY', 0)
            t_bx = tv_dump.get('borderX', 0)
        except Exception as e:
            print("TV query error:", e)
            t_leds = [(0, 0, 0)] * 228
            t_by, t_bx = 0, 0
            
        # 4-Edge Averages
        # Left (0..39), Top (40..110), Right (111..151), Bottom (152..227)
        l_left = [int(sum(l_leds[i][c] for i in range(0, 40)) / 40) for c in range(3)]
        l_top = [int(sum(l_leds[i][c] for i in range(40, 111)) / 71) for c in range(3)]
        l_right = [int(sum(l_leds[i][c] for i in range(111, 152)) / 41) for c in range(3)]
        l_bottom = [int(sum(l_leds[i][c] for i in range(152, 228)) / 76) for c in range(3)]
        
        t_left = [int(sum(t_leds[i][c] for i in range(0, 40)) / 40) for c in range(3)]
        t_top = [int(sum(t_leds[i][c] for i in range(40, 111)) / 71) for c in range(3)]
        t_right = [int(sum(t_leds[i][c] for i in range(111, 152)) / 41) for c in range(3)]
        t_bottom = [int(sum(t_leds[i][c] for i in range(152, 228)) / 76) for c in range(3)]
        
        print(f"Sample #{sample_i+1:02d}:")
        print(f"  LAPTOP (HyperHDR): Left={l_left} Top={l_top} Right={l_right} Bottom={l_bottom} [BorderY={l_by}px, BorderX={l_bx}px]")
        print(f"  TV (RenoTube):     Left={t_left} Top={t_top} Right={t_right} Bottom={t_bottom} [BorderY={t_by}%, BorderX={t_bx}%]")
        
        # Calculate Delta E / RGB differences
        diff_left = [abs(t_left[c] - l_left[c]) for c in range(3)]
        diff_top = [abs(t_top[c] - l_top[c]) for c in range(3)]
        diff_right = [abs(t_right[c] - l_right[c]) for c in range(3)]
        diff_bottom = [abs(t_bottom[c] - l_bottom[c]) for c in range(3)]
        print(f"  Delta RGB:         Left={diff_left} Top={diff_top} Right={diff_right} Bottom={diff_bottom}")
        print()
        
        comparisons.append({
            'sample': sample_i + 1,
            'laptop': {'left': l_left, 'top': l_top, 'right': l_right, 'bottom': l_bottom},
            'tv': {'left': t_left, 'top': t_top, 'right': t_right, 'bottom': t_bottom},
            'diff': {'left': diff_left, 'top': diff_top, 'right': diff_right, 'bottom': diff_bottom}
        })
        
        time.sleep(1.0)
        
    # Save comparison data
    with open(r'C:\Users\renop\.gemini\antigravity\brain\dfe7dee6-382c-4a33-958a-957356d74f51\ambilight_comparison.json', 'w') as out:
        json.dump(comparisons, out, indent=2)
    print("Saved ambilight_comparison.json successfully!")

if __name__ == '__main__':
    main()
