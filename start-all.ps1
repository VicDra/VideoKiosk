#Requires -Version 5.1
<#
.SYNOPSIS
    VideoKiosk - start all services with log analysis.
.DESCRIPTION
    1. Analyzes previous log files for errors/warnings.
    2. Stops old processes (signaling server, operator-app).
    3. Builds and starts all three components.
    4. Installs APK on connected Android device and launches app.
    5. Starts logcat capture in background.
#>

param(
    [string]$ServerIp    = "192.168.3.235",
    [int]$ServerPort     = 8080,
    [string]$KioskId     = "kiosk_1",
    [switch]$SkipBuild   = $false,
    [switch]$SkipAndroid = $false
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ROOT      = $PSScriptRoot
$LOGS_DIR  = Join-Path $ROOT "logs"
$ADB       = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$env:PATH  = "$env:PATH;C:\Program Files\Git\cmd"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

function Write-Header([string]$Text) {
    Write-Host ""
    Write-Host ("=" * 60) -ForegroundColor Cyan
    Write-Host "  $Text" -ForegroundColor Cyan
    Write-Host ("=" * 60) -ForegroundColor Cyan
}

function Write-Ok([string]$Text)   { Write-Host "  [OK]  $Text" -ForegroundColor Green  }
function Write-Warn([string]$Text) { Write-Host "  [!!]  $Text" -ForegroundColor Yellow }
function Write-Err([string]$Text)  { Write-Host "  [ERR] $Text" -ForegroundColor Red    }
function Write-Info([string]$Text) { Write-Host "  [..] $Text"  -ForegroundColor Gray   }

# ---------------------------------------------------------------------------
# 1. LOG ANALYSIS
# ---------------------------------------------------------------------------

Write-Header "Log analysis (previous run)"

$logFiles = @{
    "signaling-server" = Join-Path $LOGS_DIR "signaling-server.log"
    "operator-app"     = Join-Path $LOGS_DIR "operator-app.log"
    "kiosk-app"        = Join-Path $LOGS_DIR "kiosk-app.log"
}

$totalErrors   = 0
$totalWarnings = 0

foreach ($name in $logFiles.Keys) {
    $path = $logFiles[$name]
    if (-not (Test-Path $path)) {
        Write-Info "$name - log file not found (first run?)"
        continue
    }

    $lines = Get-Content $path -Encoding UTF8 -ErrorAction SilentlyContinue
    if (-not $lines) {
        Write-Info "$name - log is empty"
        continue
    }

    $errors   = @($lines | Where-Object { $_ -match '\[ERROR\]|ERROR|Exception|FAILED|fatal' })
    $warnings = @($lines | Where-Object { $_ -match '\[WARN\]|WARN|Warning|deprecated' })
    $totalErrors   += $errors.Count
    $totalWarnings += $warnings.Count

    if ($errors.Count -gt 0) {
        Write-Err "$name - $($errors.Count) error(s) found:"
        $errors | Select-Object -Last 5 | ForEach-Object { Write-Host "        $_" -ForegroundColor Red }
    } elseif ($warnings.Count -gt 0) {
        Write-Warn "$name - $($warnings.Count) warning(s), no errors"
        $warnings | Select-Object -Last 3 | ForEach-Object { Write-Host "        $_" -ForegroundColor Yellow }
    } else {
        Write-Ok "$name - clean ($($lines.Count) lines)"
    }
}

if ($totalErrors -gt 0) {
    Write-Warn "Summary: $totalErrors error(s), $totalWarnings warning(s) from previous run"
} else {
    Write-Ok "Previous run had no critical errors"
}

# ---------------------------------------------------------------------------
# 2. STOP OLD PROCESSES
# ---------------------------------------------------------------------------

Write-Header "Stopping old processes"

$nodeProcs = Get-Process -Name "node" -ErrorAction SilentlyContinue
foreach ($p in $nodeProcs) {
    try {
        $cmdLine = (Get-WmiObject Win32_Process -Filter "ProcessId=$($p.Id)" -ErrorAction SilentlyContinue).CommandLine
        if ($cmdLine -like "*server.js*" -or $cmdLine -like "*videokiosk*") {
            Stop-Process -Id $p.Id -Force
            Write-Ok "Signaling server (PID $($p.Id)) stopped"
        }
    } catch { }
}

$javaProcs = Get-Process -Name "java" -ErrorAction SilentlyContinue
foreach ($p in $javaProcs) {
    try {
        $cmdLine = (Get-WmiObject Win32_Process -Filter "ProcessId=$($p.Id)" -ErrorAction SilentlyContinue).CommandLine
        if ($cmdLine -like "*operator-app*" -or $cmdLine -like "*videokiosk.operator*") {
            Stop-Process -Id $p.Id -Force
            Write-Ok "Operator-app (PID $($p.Id)) stopped"
        }
    } catch { }
}

# Stop old logcat processes
Get-Process -Name "adb" -ErrorAction SilentlyContinue | ForEach-Object {
    try {
        $cmdLine = (Get-WmiObject Win32_Process -Filter "ProcessId=$($_.Id)" -ErrorAction SilentlyContinue).CommandLine
        if ($cmdLine -like "*logcat*") {
            Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
        }
    } catch { }
}

Start-Sleep -Milliseconds 500

# Create logs dir and rotate old logs
if (-not (Test-Path $LOGS_DIR)) {
    New-Item -ItemType Directory -Path $LOGS_DIR | Out-Null
}

foreach ($name in $logFiles.Keys) {
    $path = $logFiles[$name]
    if (Test-Path $path) {
        $stamp   = Get-Date -Format "yyyyMMdd_HHmmss"
        $archive = Join-Path $LOGS_DIR "${name}_${stamp}.log"
        Rename-Item -Path $path -NewName $archive -ErrorAction SilentlyContinue
    }
    # Keep only last 3 archives per component
    Get-ChildItem $LOGS_DIR -Filter "${name}_*.log" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -Skip 3 |
        Remove-Item -Force -ErrorAction SilentlyContinue
}

Write-Ok "Old processes stopped, logs rotated"

# ---------------------------------------------------------------------------
# 3. SIGNALING SERVER
# ---------------------------------------------------------------------------

Write-Header "Starting Signaling Server"

$signalingLog = $logFiles["signaling-server"]
$signalingErr = Join-Path $LOGS_DIR "signaling-server.err"

Push-Location (Join-Path $ROOT "signaling-server")
$sigProc = Start-Process `
    -FilePath "node" `
    -ArgumentList "server.js" `
    -WorkingDirectory (Join-Path $ROOT "signaling-server") `
    -RedirectStandardOutput $signalingLog `
    -RedirectStandardError  $signalingErr `
    -PassThru `
    -WindowStyle Hidden
Pop-Location

# Wait for port to be ready
$ready = $false
for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Milliseconds 300
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $tcp.Connect("localhost", $ServerPort)
        $tcp.Dispose()
        $ready = $true
        break
    } catch { }
}

if ($ready) {
    Write-Ok "Signaling server running (PID $($sigProc.Id), ws://0.0.0.0:$ServerPort)"
    Write-Info "Log: $signalingLog"
} else {
    Write-Err "Signaling server did not respond on port $ServerPort within 6s"
    if (Test-Path $signalingLog) {
        Write-Host "  Last log lines:" -ForegroundColor Yellow
        Get-Content $signalingLog -Tail 10 | ForEach-Object { Write-Host "    $_" -ForegroundColor Yellow }
    }
    if (Test-Path $signalingErr) {
        Get-Content $signalingErr | ForEach-Object { Write-Host "    STDERR: $_" -ForegroundColor Red }
    }
}

# ---------------------------------------------------------------------------
# 4. BUILD (unless skipped)
# ---------------------------------------------------------------------------

if (-not $SkipBuild) {
    Write-Header "Building components"

    Write-Info "Building operator-app..."
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.11"
    $buildResult = & (Join-Path $ROOT "operator-app\gradlew.bat") `
        -p (Join-Path $ROOT "operator-app") build -x test 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Ok "operator-app build OK"
    } else {
        Write-Err "operator-app build FAILED:"
        $buildResult | Select-Object -Last 10 | ForEach-Object { Write-Host "    $_" -ForegroundColor Red }
    }

    Write-Info "Building kiosk-app APK..."
    $apkResult = & (Join-Path $ROOT "kiosk-app\gradlew.bat") `
        -p (Join-Path $ROOT "kiosk-app") assembleDebug 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Ok "kiosk-app APK build OK"
    } else {
        Write-Err "kiosk-app build FAILED:"
        $apkResult | Select-Object -Last 10 | ForEach-Object { Write-Host "    $_" -ForegroundColor Red }
    }
}

# ---------------------------------------------------------------------------
# 5. OPERATOR APP (JavaFX)
# ---------------------------------------------------------------------------

Write-Header "Starting Operator App"

$operProc = Start-Process `
    -FilePath (Join-Path $ROOT "operator-app\gradlew.bat") `
    -ArgumentList "run" `
    -WorkingDirectory (Join-Path $ROOT "operator-app") `
    -PassThru `
    -WindowStyle Normal

Start-Sleep -Seconds 8

$javaNew = Get-Process -Name "java" -ErrorAction SilentlyContinue |
    Where-Object { $_.StartTime -gt (Get-Date).AddSeconds(-30) } |
    Select-Object -First 1

if ($javaNew) {
    Write-Ok "Operator app running (Java PID $($javaNew.Id))"
    Write-Info "Log: $($logFiles['operator-app'])"
} else {
    Write-Warn "Operator app Java process not yet detected (may still be starting)"
}

# ---------------------------------------------------------------------------
# 6. ANDROID — install APK + logcat
# ---------------------------------------------------------------------------

if (-not $SkipAndroid) {
    Write-Header "Android - install APK and start"

    if (-not (Test-Path $ADB)) {
        Write-Warn "ADB not found at $ADB - skipping Android"
    } else {
        # Use get-serialno — more reliable than parsing "adb devices" tabular output
        $deviceId = (& $ADB get-serialno 2>&1) | Where-Object { $_ -notmatch "daemon|error|List" } | Select-Object -First 1
        $deviceId = "$deviceId".Trim()
        if (-not $deviceId -or $deviceId -eq "unknown") {
            Write-Warn "No Android device connected - skipping"
        } else {
            Write-Info "Device: $deviceId"

            # ── ADB reverse tunnel ──────────────────────────────────────────
            # Forward device port 8080 → PC localhost:8080 through ADB so the
            # kiosk can reach the signaling server without a firewall rule.
            $revOut = & $ADB -s $deviceId reverse tcp:$ServerPort tcp:$ServerPort 2>&1
            Write-Ok "ADB reverse tunnel: device:$ServerPort → PC:$ServerPort ($revOut)"

            # Patch kiosk SharedPreferences to use 127.0.0.1 via the tunnel
            $PKG = "com.videokiosk.kiosk"
            $newPrefs = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" +
                        "<map>" +
                        "<string name=`"server_ip`">127.0.0.1</string>" +
                        "<string name=`"server_port`">$ServerPort</string>" +
                        "</map>"
            $shellCmd = "run-as $PKG sh -c 'cat > /data/data/$PKG/shared_prefs/kiosk_prefs.xml'"
            $newPrefs | & $ADB -s $deviceId shell $shellCmd 2>&1 | Out-Null
            Write-Ok "Kiosk settings → 127.0.0.1:$ServerPort (via ADB tunnel)"

            # ── Install APK ─────────────────────────────────────────────────
            $apkPath = Join-Path $ROOT "kiosk-app\app\build\outputs\apk\debug\app-debug.apk"
            if (Test-Path $apkPath) {
                $installOut = & $ADB -s $deviceId install -r $apkPath 2>&1
                if ("$installOut" -match "Success") {
                    Write-Ok "APK installed on $deviceId"
                } else {
                    Write-Err "APK install failed: $installOut"
                }
            } else {
                Write-Warn "APK not found: $apkPath"
            }

            # Clear old logcat
            & $ADB -s $deviceId logcat -c 2>&1 | Out-Null
            Start-Sleep -Milliseconds 300

            # Launch app
            $launchOut = & $ADB -s $deviceId shell am start -n "com.videokiosk.kiosk/.MainActivity" 2>&1
            if ("$launchOut" -match "Starting") {
                Write-Ok "Kiosk app launched on device"
            } else {
                Write-Warn "am start returned: $launchOut"
            }

            # Start logcat capture — write to file via redirect
            $kioskLog = $logFiles["kiosk-app"]
            $logcatArgs = "-s $deviceId logcat -v time MainActivity:D WebRTCClient:D SignalingClient:D MainViewModel:D *:S"
            $logcatProc = Start-Process `
                -FilePath $ADB `
                -ArgumentList $logcatArgs `
                -RedirectStandardOutput $kioskLog `
                -PassThru `
                -WindowStyle Hidden `
                -ErrorAction SilentlyContinue
            if ($logcatProc) {
                Write-Ok "Logcat capture started (PID $($logcatProc.Id))"
                Write-Info "Log: $kioskLog"
            } else {
                Write-Warn "Logcat capture could not start (non-critical)"
            }
        }
    }
}

# ---------------------------------------------------------------------------
# 7. SUMMARY
# ---------------------------------------------------------------------------

Write-Header "All services started"

Write-Host ""
Write-Host "  Component           URL" -ForegroundColor White
Write-Host "  ----------------------------------------" -ForegroundColor DarkGray
Write-Host "  Signaling Server    ws://${ServerIp}:${ServerPort}" -ForegroundColor Green
Write-Host "  Operator App        ws://${ServerIp}:${ServerPort}?role=operator" -ForegroundColor Green
Write-Host "  Kiosk (Android)     ws://${ServerIp}:${ServerPort}?role=client&id=${KioskId}" -ForegroundColor Green
Write-Host ""
Write-Host "  Logs directory: $LOGS_DIR" -ForegroundColor DarkGray
Write-Host ""
