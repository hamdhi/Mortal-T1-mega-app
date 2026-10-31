param([switch]$Release, [int]$VersionCode = 1, [string]$VersionName = '0.1.0')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17' }
if ($Release) {
    $secretsPath = Join-Path (Get-Location) '.signing/release-secrets.json'
    if (Test-Path -LiteralPath $secretsPath) {
        $signing = Get-Content -LiteralPath $secretsPath -Raw | ConvertFrom-Json
        $env:SIGNING_STORE = Join-Path (Get-Location) '.signing/mortal-release.jks'
        $env:SIGNING_PASSWORD = $signing.password
        $env:SIGNING_ALIAS = 'mortal'
        $env:SIGNING_KEY_PASSWORD = $signing.password
    }
    if (-not $env:SIGNING_STORE) { throw 'Configure SIGNING_* environment variables or a local .signing key first.' }
    & .\gradlew.bat testDebugUnitTest lintDebug assembleRelease "-PversionCode=$VersionCode" "-PversionName=$VersionName" --no-daemon --console plain
} else {
    & .\gradlew.bat testDebugUnitTest lintDebug assembleDebug "-PversionCode=$VersionCode" "-PversionName=$VersionName" --no-daemon --console plain
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
