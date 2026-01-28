$ErrorActionPreference = "Stop"

Write-Host "=== JFR-TAIL V4 (Security) Test ===" -ForegroundColor Cyan

# 1. Start Sample App (Agent with Security)
# We use a fixed secret "supersecret123" for testing
Write-Host "`n[1/3] Starting Secure Agent..." -ForegroundColor Yellow
$rootDir = Get-Location
$sampleJar = "$rootDir\sample\build\libs\sample-1.0-SNAPSHOT.jar"
$cliJar = "$rootDir\cli\build\libs\cli-1.0-SNAPSHOT.jar"

$env:JFRTAIL_SECRET = "supersecret123"
$sampleProcess = Start-Process -FilePath "java" -ArgumentList "-Djfrtail.start=true", "-Djfrtail.secret=supersecret123", "-jar", $sampleJar -PassThru -NoNewWindow
$samplePid = $sampleProcess.Id
Write-Host "-> Sample App PID: $samplePid (Secret: supersecret123)" -ForegroundColor Green

Start-Sleep -Seconds 5

# 2. Generate Token (Owner Mode)
Write-Host "`n[2/3] Generating Token..." -ForegroundColor Yellow
$token = java -jar $cliJar token --secret "supersecret123" --ttl 60
Write-Host "-> Generated Token: $token" -ForegroundColor Gray

# 3. Connect with Token
Write-Host "`n[3/3] Connecting with Token..." -ForegroundColor Yellow
try {
    # We use --actuator-url pointing to Agent itself to test HTTP Auth too
    java -jar $cliJar connect --port 7099 --token $token --actuator-url http://localhost:8080/jfr
}
catch {
    Write-Error "Failed to run JFR Tail CLI: $_"
}

# Cleanup
Stop-Process -Id $samplePid -Force
