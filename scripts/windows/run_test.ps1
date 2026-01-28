$ErrorActionPreference = "Stop"

function Assert-FileExists {
    param($Path)
    if (-not (Test-Path $Path)) {
        Write-Error "File not found: $Path"
        exit 1
    }
}

Write-Host "=== JFR-TAIL Automated Test ===" -ForegroundColor Cyan

# 1. Build Phase
Write-Host "`n[1/4] Building project..." -ForegroundColor Yellow
# Try gradlew first, then gradle
if (Test-Path "gradlew") {
    ./gradlew build
}
elseif (Get-Command "gradle" -ErrorAction SilentlyContinue) {
    gradle build
}
else {
    Write-Warning "Gradle not found. Assuming JARs are already built."
}

# Paths
$rootDir = Get-Location
$sampleJar = "$rootDir\sample\build\libs\sample-1.0-SNAPSHOT.jar"
$cliJar = "$rootDir\cli\build\libs\cli-1.0-SNAPSHOT.jar" # Fat JAR (manual)
$agentJar = "$rootDir\agent\build\libs\agent-1.0-SNAPSHOT.jar"

# Verify JARs
Assert-FileExists $sampleJar
Assert-FileExists $cliJar
Assert-FileExists $agentJar

# 2. Start Sample App
Write-Host "`n[2/4] Starting Sample App..." -ForegroundColor Yellow
$sampleProcess = Start-Process -FilePath "java" -ArgumentList "-jar", $sampleJar -PassThru -NoNewWindow
if (-not $sampleProcess) {
    Write-Error "Failed to start Sample App"
    exit 1
}
$samplePid = $sampleProcess.Id
Write-Host "-> Sample App running with PID: $samplePid" -ForegroundColor Green

# Give it a moment to initialize
Start-Sleep -Seconds 2

# 3. Attach JFR Tail
Write-Host "`n[3/4] Attaching JFR Tail CLI..." -ForegroundColor Yellow
Write-Host "-> Events should appear in the TUI window. Close the TUI (press 'q') to finish the test." -ForegroundColor Gray

# We run the CLI in the current window (Wait) so the user interacts with it here
try {
    java -jar $cliJar attach --pid $samplePid --agent-jar $agentJar
}
catch {
    Write-Error "Failed to run JFR Tail CLI"
}

# 4. Cleanup
Write-Host "`n[4/4] Cleaning up..." -ForegroundColor Yellow
if (-not $sampleProcess.HasExited) {
    Stop-Process -Id $samplePid -Force
    Write-Host "-> Sample App (PID $samplePid) stopped." -ForegroundColor Green
}
else {
    Write-Host "-> Sample App already exited." -ForegroundColor Yellow
}

Write-Host "`nTest Complete." -ForegroundColor Cyan
