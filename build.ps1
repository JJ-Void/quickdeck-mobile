$env:JAVA_HOME = "D:\tools\jdk\jdk-17.0.20.1+1"
$env:ANDROID_HOME = "D:\android-sdk"
$env:PATH = "$env:JAVA_HOME\bin;D:\tools\gradle-8.9\bin;D:\android-sdk\platform-tools;" + $env:PATH
Set-Location D:\quickdeck-mobile-main
$task = if ($args.Count -gt 0) { $args[0] } else { "assembleRelease" }

# Ширина буфера: без неё PowerShell рвёт строки по ширине окна,
# и сообщения компилятора приходят кусками без файла и номера строки.
try { $Host.UI.RawUI.BufferSize = New-Object Management.Automation.Host.Size(500, 3000) } catch {}

gradle $task 2>&1 | Out-String -Width 500 | Out-File D:\build.log -Encoding utf8
"EXIT $LASTEXITCODE" | Out-File D:\build.log -Append -Encoding utf8
