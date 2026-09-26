param([string[]]$Tasks = @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    # Keep this project's tooling separate from other Android projects.
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools/gradle-home'
    $verifiedGradle = Join-Path $projectRoot '.tools/gradle-verified/gradle-8.13/bin/gradle.bat'
    if (Test-Path -LiteralPath $verifiedGradle) {
        & $verifiedGradle @Tasks
    } else {
        & (Join-Path $projectRoot 'gradlew.bat') @Tasks
    }
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
