$ErrorActionPreference = 'Stop'
$windowsRoot = Split-Path $PSScriptRoot -Parent
& (Join-Path $windowsRoot '.venv/Scripts/pythonw.exe') (Join-Path $windowsRoot 'launcher.py')
