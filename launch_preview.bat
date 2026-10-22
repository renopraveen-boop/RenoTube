@echo off
set "PATH=C:\Users\renop\Downloads\renotube\android-sdk\platform-tools;%PATH%"
set "ADB=C:\Users\renop\Downloads\renotube\android-sdk\platform-tools\adb.exe"
for /f "delims=" %%i in ('dir /b /s "C:\Users\renop\AppData\Local\Microsoft\WinGet\Packages\*scrcpy.exe"') do (
    start "" "%%i" --window-title "RenoTube TV - Laptop" --keyboard=sdk
    goto :done
)
:done
