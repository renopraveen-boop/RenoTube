import ctypes
import time
import subprocess
from PIL import Image

user32 = ctypes.windll.user32
gdi32 = ctypes.windll.gdi32

hwinsta = user32.OpenWindowStationW('winsta0', False, 0x0000037F)
if hwinsta: user32.SetProcessWindowStation(hwinsta)
hdesk = user32.OpenDesktopW('default', 0, False, 0x000001FF)
if hdesk: user32.SetThreadDesktop(hdesk)

# Find Brave
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

EnumWindowsProc = ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)
user32.EnumWindows(EnumWindowsProc(enum_cb), 0)

if brave_hwnd:
    user32.ShowWindow(brave_hwnd, 3) # MAXIMIZE
    user32.SetForegroundWindow(brave_hwnd)
    time.sleep(0.5)

    # Set Clipboard
    url = 'https://www.youtube.com/watch?v=rN9F6-tbySY'
    subprocess.run(['powershell', '-Command', f'Set-Clipboard -Value "{url}"'], capture_output=True)

    VK_CONTROL = 0x11
    VK_L = 0x4C
    VK_V = 0x56
    VK_RETURN = 0x0D

    # Ctrl + L
    user32.keybd_event(VK_CONTROL, 0, 0, 0)
    user32.keybd_event(VK_L, 0, 0, 0)
    time.sleep(0.08)
    user32.keybd_event(VK_L, 0, 2, 0)
    user32.keybd_event(VK_CONTROL, 0, 2, 0)
    time.sleep(0.2)

    # Ctrl + V
    user32.keybd_event(VK_CONTROL, 0, 0, 0)
    user32.keybd_event(VK_V, 0, 0, 0)
    time.sleep(0.08)
    user32.keybd_event(VK_V, 0, 2, 0)
    user32.keybd_event(VK_CONTROL, 0, 2, 0)
    time.sleep(0.2)

    # Enter
    user32.keybd_event(VK_RETURN, 0, 0, 0)
    time.sleep(0.08)
    user32.keybd_event(VK_RETURN, 0, 2, 0)
    print('Navigated to URL!')

    # Wait for page to load
    time.sleep(4)

    # Press 'k' (play if paused)
    user32.keybd_event(0x4B, 0, 0, 0)
    time.sleep(0.05)
    user32.keybd_event(0x4B, 0, 2, 0)
    time.sleep(0.5)

    # Press 'f' (fullscreen)
    user32.keybd_event(0x46, 0, 0, 0)
    time.sleep(0.05)
    user32.keybd_event(0x46, 0, 2, 0)
    print('Toggled fullscreen with F!')
    time.sleep(2)

# Grab desktop
user32.SetProcessDPIAware()
w = user32.GetSystemMetrics(0)
h = user32.GetSystemMetrics(1)
hdc_screen = user32.GetDC(0)
hdc_mem = gdi32.CreateCompatibleDC(hdc_screen)
hbm = gdi32.CreateCompatibleBitmap(hdc_screen, w, h)
gdi32.SelectObject(hdc_mem, hbm)
gdi32.BitBlt(hdc_mem, 0, 0, w, h, hdc_screen, 0, 0, 0x00CC0020)

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
bmi.biWidth = w
bmi.biHeight = -h
bmi.biPlanes = 1
bmi.biBitCount = 32
bmi.biCompression = 0

buf = ctypes.create_string_buffer(w * h * 4)
gdi32.GetDIBits(hdc_mem, hbm, 0, h, buf, ctypes.byref(bmi), 0)

im = Image.frombuffer('RGBA', (w, h), buf, 'raw', 'BGRA', 0, 1)
im.save(r'C:\Users\renop\.gemini\antigravity\brain\dfe7dee6-382c-4a33-958a-957356d74f51\laptop_youtube_playing.png')
print('Captured laptop_youtube_playing.png!')

gdi32.DeleteObject(hbm)
gdi32.DeleteDC(hdc_mem)
user32.ReleaseDC(0, hdc_screen)
