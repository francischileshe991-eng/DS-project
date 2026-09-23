# scripts/stop-cluster.ps1
# Gracefully stops all cluster Java Node processes

$ErrorActionPreference = "Continue"

Write-Host "Stopping all running Node processes..." -ForegroundColor Yellow

$procs = Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match "Node \d+ \d+" }

if ($procs) {
    foreach ($p in $procs) {
        Write-Host "  Terminating PID $($p.ProcessId) ($($p.CommandLine))..." -ForegroundColor Gray
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    }
    Write-Host "All cluster nodes stopped." -ForegroundColor Green
} else {
    Write-Host "No active cluster node processes found." -ForegroundColor Gray
}
