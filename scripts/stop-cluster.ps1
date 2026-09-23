# scripts/stop-cluster.ps1
# Gracefully stops local Java Node processes across Windows, Linux, and macOS

$ErrorActionPreference = "Continue"

Write-Host "Stopping local running Node processes..." -ForegroundColor Yellow

$killed = 0

if ($IsWindows -or ($env:OS -like "*Windows*")) {
    try {
        $procs = Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object { 
            ($_.CommandLine -match "java" -and $_.CommandLine -match "Node\s+\d+")
        }
        if ($procs) {
            foreach ($p in $procs) {
                Write-Host "  Terminating PID $($p.ProcessId) ($($p.CommandLine))..." -ForegroundColor Gray
                Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
                $killed++
            }
        }
    } catch {
        Get-Process -Name "java" -ErrorAction SilentlyContinue | ForEach-Object {
            Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
            $killed++
        }
    }
} else {
    # Linux / macOS (PowerShell Core)
    try {
        $pids = (pgrep -f "Node\s+\d+" 2>$null)
        if ($pids) {
            foreach ($pidNum in ($pids -split "`n")) {
                if ($pidNum.Trim()) {
                    Stop-Process -Id ([int]$pidNum.Trim()) -Force -ErrorAction SilentlyContinue
                    $killed++
                }
            }
        }
    } catch {}
}

if ($killed -gt 0) {
    Write-Host "All local cluster nodes stopped ($killed processes terminated)." -ForegroundColor Green
} else {
    Write-Host "No active local cluster node processes found." -ForegroundColor Gray
}
Write-Host "Note: For multi-machine setups, stop each remote node on its respective laptop (Ctrl+C or 'quit').`n" -ForegroundColor DarkGray
