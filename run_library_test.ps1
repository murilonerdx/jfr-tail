$ErrorActionPreference = "Stop"

Write-Host "=== JFR-TAIL Library Mode Test ===" -ForegroundColor Cyan

# 1. Build Phase
Write-Host "`n[1/4] Building project..." -ForegroundColor Yellow
$buildCmd = if (Test-Path "gradlew") { ".\gradlew" } else { "gradle" }
& $buildCmd build -x test
if ($LASTEXITCODE -ne 0) { 
    Write-Warning "Build failed or had issues. Proceeding if artifacts exist..."
}

# Paths
$rootDir = Get-Location
$sampleJar = "$rootDir\sample\build\libs\sample-1.0-SNAPSHOT.jar"
$cliJar = "$rootDir\cli\build\libs\cli-1.0-SNAPSHOT.jar"

# 2. Start Sample App (Library Mode)
Write-Host "`n[2/4] Starting Sample App (Library Mode)..." -ForegroundColor Yellow
Write-Host "-> This starts the JFR Tail Agent INTERNALLY via API." -ForegroundColor Gray
$sampleProcess = Start-Process -FilePath "java" -ArgumentList "-Djfrtail.start=true", "-jar", $sampleJar -PassThru -NoNewWindow
$samplePid = $sampleProcess.Id
Write-Host "-> Sample App running with PID: $samplePid" -ForegroundColor Green

Start-Sleep -Seconds 5

# 3. Connect JFR Tail CLI
Write-Host "`n[3/4] Connecting JFR Tail CLI..." -ForegroundColor Yellow
Write-Host "-> Web Dashboard should be at: http://localhost:8080/jfr/dashboard" -ForegroundColor Gray
Write-Host "-> JSON Stats should be at: http://localhost:8080/jfr/stats" -ForegroundColor Gray

try {
    # Use 'connect' command instead of 'attach'
    java -jar $cliJar connect --port 7099
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
