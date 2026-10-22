& "C:\Users\renop\Downloads\renotube\focus_brave.ps1"
Start-Sleep -Milliseconds 600
$wshell = New-Object -ComObject Wscript.Shell
$wshell.AppActivate('Brave')
Start-Sleep -Milliseconds 400
$wshell.SendKeys('{F11}')
Start-Sleep -Milliseconds 400
$wshell.SendKeys('f')
Start-Sleep -Milliseconds 400
$wshell.SendKeys('k')
Write-Host "Keys sent successfully"
