# scripts/compile.ps1
# Compiles all Java sources into the out directory

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

if (!(Test-Path "out")) {
    New-Item -ItemType Directory -Path "out" | Out-Null
}

Write-Host "Compiling Java sources..." -ForegroundColor Cyan
javac -d out src/util/*.java src/models/*.java src/api/*.java src/sync/*.java src/*.java

if ($LASTEXITCODE -eq 0) {
    Write-Host "Build Successful! Class files generated in out/" -ForegroundColor Green
} else {
    Write-Host "Build Failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit 1
}
