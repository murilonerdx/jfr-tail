$ErrorActionPreference = "Stop"

Write-Host "=== JFR-TAIL V3 (Spring/Actuator) Test ===" -ForegroundColor Cyan

# 1. Start Mock Actuator
Write-Host "`n[1/3] Starting Mock Actuator (Python)..." -ForegroundColor Yellow
$actuatorProcess = Start-Process -FilePath "python" -ArgumentList "mock_actuator.py" -PassThru -NoNewWindow
$actuatorPid = $actuatorProcess.Id
Write-Host "-> Mock Actuator PID: $actuatorPid (Port 8081)" -ForegroundColor Green

Start-Sleep -Seconds 2

# 2. Start Sample App (Standard JFR Source)
Write-Host "`n[2/3] Starting Sample JFR App..." -ForegroundColor Yellow
$rootDir = Get-Location
$sampleJar = "$rootDir\sample\build\libs\sample-1.0-SNAPSHOT.jar"
$cliJar = "$rootDir\cli\build\libs\cli-1.0-SNAPSHOT.jar"

# Start sample app (Agent Mode listening on 7099, Web 8080)
$sampleProcess = Start-Process -FilePath "java" -ArgumentList "-Djfrtail.start=true", "-jar", $sampleJar -PassThru -NoNewWindow
$samplePid = $sampleProcess.Id
Write-Host "-> Sample App PID: $samplePid" -ForegroundColor Green

Start-Sleep -Seconds 5

# 3. Connect CLI with Actuator Args
Write-Host "`n[3/3] Connect CLI with Actuator Support..." -ForegroundColor Yellow
Write-Host "-> Press 'S' to see Spring Panel. 'Q' to quit." -ForegroundColor Gray
try {
    # Connect and point to Mock Actuator
    java -jar $cliJar connect --port 7099 --actuator-url http://localhost:8081/actuator
}
catch {
    Write-Error "Failed to run JFR Tail CLI: $_"
}

# Cleanup
Stop-Process -Id $actuatorPid -Force
Stop-Process -Id $samplePid -Force
