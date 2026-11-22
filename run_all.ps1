$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$TestDir = Join-Path $RootDir "test-server"
$ReportFile = Join-Path $RootDir "neon-report.txt"
$BuildLog = Join-Path $RootDir "build.log"
$UserAgent = "hordes-paper-testkit/1.0.0 (https://github.com/SmileycorpMC/The-Hordes)"
$McVersion = "26.3"

Start-Transcript -Path $ReportFile -Force | Out-Null
try {
    Write-Host "================================================================"
    Write-Host "GEMINI NEON - HordesPaper Automated Build & Acceptance Test Run"
    Write-Host "Timestamp : $(Get-Date -Format o)"
    Write-Host "Host OS   : $([System.Environment]::OSVersion.VersionString)"
    Write-Host "CPU Cores : $([System.Environment]::ProcessorCount)"
    Write-Host "================================================================"

    Write-Host "`n[1/6] Checking JDK 25..."
    $javaVer = (& java -version 2>&1 | Select-Object -First 1)
    Write-Host "Detected Java: $javaVer"
    if ($javaVer -notmatch '"25(\.|")') {
        throw "JDK 25 is required for Paper 26.3. Found: $javaVer"
    }

    Write-Host "`n[2/6] Building HordesPaper and neon-testkit with gradle..."
    Push-Location $RootDir
    if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
        throw "'gradle' not found in PATH. Install Gradle 9.1.0+ and re-run."
    }
    & gradle clean build --no-daemon 2>&1 | Tee-Object -FilePath $BuildLog
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed (exit code $LASTEXITCODE). See build.log."
    }
    Pop-Location

    $mainJar = Get-ChildItem (Join-Path $RootDir "build\libs") -Filter "HordesPaper-*.jar" | Where-Object { $_.Name -notlike "*-plain.jar" } | Select-Object -First 1
    $kitJar = Get-ChildItem (Join-Path $RootDir "neon-testkit\build\libs") -Filter "neon-testkit-*.jar" | Where-Object { $_.Name -notlike "*-plain.jar" } | Select-Object -First 1
    if (-not $mainJar -or -not $kitJar) { throw "Built JAR(s) not found." }
    Write-Host "Main JAR    : $($mainJar.FullName)"
    Write-Host "Testkit JAR : $($kitJar.FullName)"

    Write-Host "`n[3/6] Downloading latest STABLE Paper $McVersion build..."
    $pluginsDir = Join-Path $TestDir "plugins"
    New-Item -ItemType Directory -Force -Path $pluginsDir | Out-Null
    $builds = Invoke-RestMethod -Uri "https://fill.papermc.io/v3/projects/paper/versions/$McVersion/builds" -Headers @{ "User-Agent" = $UserAgent }
    $stable = $builds | Where-Object { $_.channel -eq "STABLE" } | Select-Object -First 1
    if (-not $stable) { throw "No STABLE Paper $McVersion build found." }
    $paperUrl = $stable.downloads."server:default".url
    Write-Host "Paper $McVersion stable build #$($stable.id) -> $paperUrl"
    Invoke-WebRequest -Uri $paperUrl -Headers @{ "User-Agent" = $UserAgent } -OutFile (Join-Path $TestDir "paper.jar")

    Write-Host "`n[4/6] Preparing sandbox test-server (EULA accepted ONLY in test-server/)..."
    Set-Content -Path (Join-Path $TestDir "eula.txt") -Value "eula=true" -Encoding ASCII
    @"
online-mode=false
enable-rcon=false
level-type=minecraft\:flat
spawn-protection=0
simulation-distance=10
view-distance=10
difficulty=hard
motd=NeonTestkit Headless Sandbox
max-players=5
"@ | Set-Content -Path (Join-Path $TestDir "server.properties") -Encoding ASCII

    Get-ChildItem $pluginsDir -Filter "HordesPaper-*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
    Get-ChildItem $pluginsDir -Filter "neon-testkit-*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
    Copy-Item $mainJar.FullName $pluginsDir -Force
    Copy-Item $kitJar.FullName $pluginsDir -Force

    Write-Host "`n[5/6] Booting Paper $McVersion headless with auto-run AT-01..AT-23..."
    Push-Location $TestDir
    Remove-Item "neon-testkit-results.txt" -ErrorAction SilentlyContinue
    & java -Xms1G -Xmx2G "-Dneon.autorun=true" "-Dneon.autoshutdown=true" -jar paper.jar --nogui
    Pop-Location

    Write-Host "`n[6/6] Collecting acceptance test results & evaluating strict per-ID gate..."
    Write-Host "---------------- NEON TESTKIT RESULTS ----------------"
    $resPath = Join-Path $TestDir "neon-testkit-results.txt"
    if (-not (Test-Path $resPath)) {
        throw "neon-testkit-results.txt was not generated! Status: UNVERIFIED"
    }
    $lines = Get-Content $resPath
    $lines | Write-Host

    Write-Host "---------------- SERVER ERRORS/WARNINGS --------------"
    $logPath = Join-Path $TestDir "logs\latest.log"
    if (Test-Path $logPath) {
        Select-String -Path $logPath -Pattern "ERROR|WARN|Exception|HordesPaper|NeonTestkit" | Select-Object -Last 120 | ForEach-Object { Write-Host $_.Line }
    }
    Write-Host "================================================================"
    Write-Host "Report saved to: $ReportFile"

    $coreRequired = @("AT-01","AT-02","AT-03","AT-04","AT-05","AT-06","AT-07","AT-09","AT-10","AT-11","AT-12","AT-13","AT-14","AT-15","AT-16","AT-17","AT-20","AT-22","AT-23")
    $tickRequired = @("AT-18","AT-19")
    $hasFail = ($lines | Where-Object { $_ -match '^\[FAIL\] ' }).Count -gt 0
    $missingCore = $false
    $tickSkipped = $false
    $tickFail = $false

    foreach ($id in $coreRequired) {
        if (-not ($lines | Where-Object { $_ -match "^\[PASS\] $id " })) {
            Write-Host "MISSING OR NON-PASS core required test: $id"
            $missingCore = $true
        }
    }
    foreach ($id in $tickRequired) {
        if ($lines | Where-Object { $_ -match "^\[PASS\] $id " }) {
            continue
        } elseif ($lines | Where-Object { $_ -match "^\[INFO\] $id .*SKIPPED:" }) {
            Write-Host "Headless tick-skipped test: $id"
            $tickSkipped = $true
        } else {
            Write-Host "MISSING OR FAIL tick-dependent test: $id"
            $tickFail = $true
        }
    }

    if (-not $hasFail -and -not $missingCore -and -not $tickFail) {
        if (-not $tickSkipped) {
            Write-Host "VERIFICATION STATUS: VERIFIED"
        } else {
            Write-Host "VERIFICATION STATUS: VERIFIED-PARTIAL (AT-18/AT-19 skipped on headless server without active player ticking)"
        }
    } else {
        throw "VERIFICATION STATUS: UNVERIFIED"
    }
} finally {
    Stop-Transcript | Out-Null
}
