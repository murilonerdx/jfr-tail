$ErrorActionPreference = "Stop"

Write-Host "=== JFR-TAIL Actuator Test ===" -ForegroundColor Cyan

# 1. Start Mock Actuator (Background)
Write-Host "`n[1/3] Starting Mock Actuator (Java)..." -ForegroundColor Yellow
$mockSrc = "$PSScriptRoot\MockActuator.java"
if (-not (Test-Path "$PSScriptRoot\MockActuator.class")) {
    javac $mockSrc
}
$mockProcess = Start-Process -FilePath "java" -ArgumentList "-cp", "$PSScriptRoot", "MockActuator" -PassThru -NoNewWindow
$mockPid = $mockProcess.Id
Write-Host "-> Mock Actuator PID: $mockPid" -ForegroundColor Green

Start-Sleep -Seconds 2

# 2. Run CLI with Actuator connection
Write-Host "`n[2/3] Running CLI (Connect to Mock)..." -ForegroundColor Yellow
Write-Host "Expect to see: 'Requests: 120 | Total Time: 5.00s'" -ForegroundColor Magenta
Write-Host "Press 'q' to exit the CLI manually." -ForegroundColor Gray

$cliJar = "$PSScriptRoot\..\..\cli\build\libs\cli-1.0-SNAPSHOT.jar"
# We use connect command but point to a non-existent agent port (9999) just to test the UI/Actuator part
# The UI will show connection errors for the Agent but the Spring Panel should work.
java -jar $cliJar connect --port 9999 --actuator-url http://localhost:8081

# 3. Cleanup
Stop-Process -Id $mockPid -Force
Write-Host "`n[3/3] Cleanup Done." -ForegroundColor Green
