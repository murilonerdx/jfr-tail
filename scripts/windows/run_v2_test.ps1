$ErrorActionPreference = "Stop"

Write-Host "=== JFR-TAIL V2 Automated Test ===" -ForegroundColor Cyan

# 1. Build Phase
Write-Host "`n[1/4] Building project..." -ForegroundColor Yellow
if (Test-Path "gradlew") {
    CMD /C "gradlew.bat clean build"
    if ($LASTEXITCODE -ne 0) { Write-Error "Build failed"; exit 1 }
}
else {
    gradle clean build
    if ($LASTEXITCODE -ne 0) { Write-Error "Build failed"; exit 1 }
}

# Paths
$rootDir = Get-Location
$sampleJar = "$rootDir\sample\build\libs\sample-1.0-SNAPSHOT.jar"
$cliJar = "$rootDir\cli\build\libs\cli-1.0-SNAPSHOT.jar"
$agentJar = "$rootDir\agent\build\libs\agent-1.0-SNAPSHOT.jar"
$recordFile = "$rootDir\test-events.jsonl"

# 2. Start Sample App
Write-Host "`n[2/4] Starting Sample App..." -ForegroundColor Yellow
$sampleProcess = Start-Process -FilePath "java" -ArgumentList "-jar", $sampleJar -PassThru -NoNewWindow
$samplePid = $sampleProcess.Id
Write-Host "-> Sample App running with PID: $samplePid" -ForegroundColor Green

Start-Sleep -Seconds 2

# 3. Attach JFR Tail (V2)
Write-Host "`n[3/4] Attaching JFR Tail CLI (with Recording & Web)..." -ForegroundColor Yellow
Write-Host "-> TUI should open." -ForegroundColor Gray
Write-Host "-> Events will be recorded to: $recordFile" -ForegroundColor Gray
Write-Host "-> Web Dashboard will be at: http://localhost:8080" -ForegroundColor Gray

try {
    # Added --record and --web-port
    java -jar $cliJar attach --pid $samplePid --agent-jar $agentJar --record $recordFile --web-port 8080
}
catch {
    Write-Error "Failed to run JFR Tail CLI"
}

# 4. Cleanup
Write-Host "`n[4/4] Cleaning up..." -ForegroundColor Yellow
if (-not $sampleProcess.HasExited) {
    Stop-Process -Id $samplePid -Force
}

Write-Host "`nTest Complete." -ForegroundColor Cyan
