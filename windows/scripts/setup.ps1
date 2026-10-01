param([string]$Python = 'python')
$ErrorActionPreference = 'Stop'
$windowsRoot = Split-Path $PSScriptRoot -Parent
Push-Location $windowsRoot
try {
    if (-not (Test-Path '.venv/Scripts/python.exe')) {
        & $Python -m venv .venv
        if ($LASTEXITCODE -ne 0) { throw 'Failed to create virtual environment' }
    }
    & .venv/Scripts/python.exe -m pip install -r requirements-lock.txt
    if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed' }
    & .venv/Scripts/python.exe -m pip install -e . --no-deps --no-build-isolation
    if ($LASTEXITCODE -ne 0) { throw 'Project installation failed' }
    & .venv/Scripts/python.exe scripts/prepare_models.py
    if ($LASTEXITCODE -ne 0) { throw 'Model preparation failed' }
} finally { Pop-Location }
