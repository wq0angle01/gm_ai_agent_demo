# Local build helper: neither java nor mvn is on PATH on this machine.
# Usage:
#   powershell -File scripts\build.ps1                 # mvn test
#   powershell -File scripts\build.ps1 spring-boot:run # run the app
#   powershell -File scripts\build.ps1 clean package   # build the jar
# No Chinese literals here on purpose: Windows PowerShell 5.1 reads UTF-8-without-BOM
# files as GBK, which mangles CJK paths and can even break parsing.
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Goals = @('test'))

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

# --- locate JDK (prefer 21, fall back to any JDK under ~/.jdks) ---
$jdkDirs = @("$env:USERPROFILE\.jdks", 'C:\Program Files\Java')
$javaHome = $null
foreach ($d in $jdkDirs) {
    if (-not (Test-Path $d)) { continue }
    $javaHome = (Get-ChildItem $d -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } |
        Sort-Object { if ($_.Name -match 'corretto-21') { 0 } elseif ($_.Name -match '21') { 1 } else { 2 } } |
        Select-Object -First 1).FullName
    if ($javaHome) { break }
}
if (-not $javaHome) { throw 'No JDK found under ~/.jdks. Install JDK 21 or edit $jdkDirs.' }
$env:JAVA_HOME = $javaHome
Write-Host "JAVA_HOME = $javaHome"

# --- locate Maven (PATH first, then the one bundled with IntelliJ) ---
$mvn = (Get-Command mvn.cmd -ErrorAction SilentlyContinue).Source
if (-not $mvn -and $env:MAVEN_HOME) { $mvn = Join-Path $env:MAVEN_HOME 'bin\mvn.cmd' }
if (-not $mvn) {
    # look for an "IntelliJ IDEA *" install one level below a drive root,
    # without hardcoding a non-ASCII path literal
    $idea = Get-ChildItem 'E:\', 'C:\', 'D:\' -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Get-ChildItem $_.FullName -Directory -ErrorAction SilentlyContinue |
                         Where-Object { $_.Name -like 'IntelliJ IDEA*' } } |
        Select-Object -First 1
    if ($idea) {
        $known = Join-Path $idea.FullName 'plugins\maven\lib\maven3\bin\mvn.cmd'
        if (Test-Path $known) {
            $mvn = $known
        } else {
            $mvn = (Get-ChildItem $idea.FullName -Recurse -Depth 8 -Filter 'mvn.cmd' -ErrorAction SilentlyContinue |
                Select-Object -First 1).FullName
        }
    }
}
if (-not $mvn) { throw 'Maven not found. Install Maven or point $env:MAVEN_HOME at it.' }
Write-Host "MVN       = $mvn"

Set-Location $root
Write-Host "GOALS     = $($Goals -join ' ')"
& $mvn -B @Goals
exit $LASTEXITCODE
