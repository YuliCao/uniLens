$ErrorActionPreference = 'Stop'
$windowsRoot = Split-Path $PSScriptRoot -Parent
$pythonPath = Join-Path $windowsRoot '.venv/Scripts/python.exe'
Push-Location $windowsRoot
try {
    & $pythonPath -m pytest tests -q
    if ($LASTEXITCODE -ne 0) { throw 'Unit tests failed' }
    & $pythonPath scripts/prepare_models.py
    if ($LASTEXITCODE -ne 0) { throw 'Model verification failed' }
    & $pythonPath scripts/collect_licenses.py
    if ($LASTEXITCODE -ne 0) { throw 'License collection failed' }
    & $pythonPath -m PyInstaller --noconfirm YomiLens.spec
    if ($LASTEXITCODE -ne 0) { throw 'Packaging failed' }
} finally { Pop-Location }
