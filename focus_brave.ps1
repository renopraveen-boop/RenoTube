Add-Type @"
  using System;
  using System.Runtime.InteropServices;
  using System.Text;
  public class User32 {
    [DllImport("user32.dll")]
    public static extern bool EnumWindows(EnumWindowsProc enumProc, IntPtr lParam);
    public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);
    [DllImport("user32.dll")]
    public static extern int GetWindowText(IntPtr hWnd, StringBuilder strText, int maxCount);
    [DllImport("user32.dll")]
    public static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")]
    public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
  }
"@

$braveWindows = @()
[User32]::EnumWindows({
    param($hwnd, $lparam)
    if ([User32]::IsWindowVisible($hwnd)) {
        $sb = New-Object System.Text.StringBuilder 512
        [User32]::GetWindowText($hwnd, $sb, 512) | Out-Null
        $title = $sb.ToString()
        if ($title -match "Brave|YouTube") {
            $script:braveWindows += [PSCustomObject]@{ Handle = $hwnd; Title = $title }
        }
    }
    return $true
}, [IntPtr]::Zero)

Write-Host "Found Brave Windows:"
foreach ($w in $braveWindows) {
    Write-Host "  Hwnd: $($w.Handle) -> $($w.Title)"
    # Maximize and bring to foreground
    [User32]::ShowWindow($w.Handle, 3) # SW_MAXIMIZE = 3
    [User32]::SetForegroundWindow($w.Handle)
}
