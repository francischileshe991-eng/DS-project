# scripts/start-cluster.ps1
# Starts N distributed nodes on ports basePort .. basePort + N - 1

param(
    [int]$Nodes = 3,
    [int]$BasePort = 8000,
    [switch]$OpenWindows
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

# Ensure compiled
& "$PSScriptRoot\compile.ps1"

if (!(Test-Path "logs")) {
    New-Item -ItemType Directory -Path "logs" | Out-Null
}

Write-Host "======================================================" -ForegroundColor Cyan
Write-Host " Starting Distributed Cluster: $Nodes Nodes (Ports $BasePort-$($BasePort + $Nodes - 1))" -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Cyan

$pids = @()
for ($id = 0; $id -lt $Nodes; $id++) {
    $port = $BasePort + $id
    $logFile = "$ProjectRoot\logs\node$id.log"
    $errFile = "$ProjectRoot\logs\node$id.err"
    if (Test-Path $logFile) { Remove-Item $logFile -Force }
    if (Test-Path $errFile) { Remove-Item $errFile -Force }

    $cmdArgs = "-cp out Node $id $port $Nodes $BasePort"
    if ($OpenWindows) {
        $proc = Start-Process -FilePath "java" -ArgumentList $cmdArgs -WorkingDirectory $ProjectRoot -PassThru
    } else {
        $proc = Start-Process -FilePath "java" -ArgumentList $cmdArgs -WorkingDirectory $ProjectRoot -PassThru -RedirectStandardOutput $logFile -RedirectStandardError $errFile -WindowStyle Hidden
    }
    $pids += $proc.Id
    Write-Host "  -> Node $id launched (PID $($proc.Id)) on port $port" -ForegroundColor Gray
}

# Wait for all nodes to become healthy
Write-Host "`nWaiting for nodes to initialize..." -NoNewline
for ($id = 0; $id -lt $Nodes; $id++) {
    $port = $BasePort + $id
    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        try {
            $resp = Invoke-RestMethod -Uri "http://localhost:$port/api/health" -TimeoutSec 1 -ErrorAction Stop
            if ($resp.status -eq "ALIVE") {
                $ready = $true
                break
            }
        } catch {
            Start-Sleep -Milliseconds 250
        }
    }
    if (!$ready) {
        Write-Host " Failed!" -ForegroundColor Red
        Write-Host "Node $id on port $port failed to report ALIVE. Check logs\node$id.err" -ForegroundColor Red
        exit 1
    }
}
Write-Host " All nodes ALIVE!`n" -ForegroundColor Green

Write-Host "Cluster Status Summary:" -ForegroundColor Yellow
for ($id = 0; $id -lt $Nodes; $id++) {
    $port = $BasePort + $id
    $status = Invoke-RestMethod -Uri "http://localhost:$port/api/status"
    $leadText = if ($status.is_leader) { "[👑 LEADER]" } else { "Leader: Node $($status.leader)" }
    $tokText = if ($status.has_token) { "[🔑 TOKEN]" } else { "" }
    Write-Host "  Node $id (Port $port) -> Lamport: $($status.lamport) | Vector: $(ConvertTo-Json -Compress $status.vector) | $leadText $tokText" -ForegroundColor White
}

Write-Host "`nAccess Web Dashboards in your browser:" -ForegroundColor Cyan
for ($id = 0; $id -lt $Nodes; $id++) {
    $port = $BasePort + $id
    Write-Host "  Node $id Dashboard: http://localhost:$port/" -ForegroundColor Green
}
Write-Host "`nTo stop the cluster, run: .\scripts\stop-cluster.ps1`n" -ForegroundColor Gray
