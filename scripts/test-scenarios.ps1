# scripts/test-scenarios.ps1
# Automated verification test harness for CSC 4722 Course Project
# Validates all 4 core distributed systems concepts against the 100-mark rubric.
# Supports both:
#   1. Local single-machine cluster: .\scripts\test-scenarios.ps1
#   2. Remote multi-PC cluster:      .\scripts\test-scenarios.ps1 -Remote [-ConfigFile cluster.cfg]

param(
    [string]$ConfigFile = "",
    [switch]$Remote
)

$ErrorActionPreference = "Continue"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host " CSC 4722 COURSE PROJECT: AUTOMATED SYSTEM TEST HARNESS " -ForegroundColor Cyan
Write-Host "======================================================================`n" -ForegroundColor Cyan

$isRemote = $false
$url0 = "http://localhost:8000"
$url1 = "http://localhost:8001"
$url2 = "http://localhost:8002"

if ($Remote -or ($ConfigFile -and (Test-Path $ConfigFile))) {
    $cfgPath = if ($ConfigFile) { $ConfigFile } else { "cluster.cfg" }
    if (Test-Path $cfgPath) {
        Write-Host "Loading cluster endpoints from $cfgPath..." -ForegroundColor Cyan
        $endpoints = @()
        Get-Content $cfgPath | ForEach-Object {
            $line = $_.Trim()
            if ($line -and !$line.StartsWith("#") -and $line.Contains("=")) {
                $parts = $line.Split("=", 2)
                $addr = $parts[1].Trim()
                if (!$addr.StartsWith("http://") -and !$addr.StartsWith("https://")) {
                    $addr = "http://$addr"
                }
                $endpoints += $addr
            }
        }
        if ($endpoints.Count -ge 3) {
            $url0 = $endpoints[0]
            $url1 = $endpoints[1]
            $url2 = $endpoints[2]
            $isRemote = $true
            Write-Host "  Target Node 0: $url0" -ForegroundColor Gray
            Write-Host "  Target Node 1: $url1" -ForegroundColor Gray
            Write-Host "  Target Node 2: $url2" -ForegroundColor Gray
        }
        else {
            Write-Host "Warning: $cfgPath contained fewer than 3 endpoints. Falling back to localhost." -ForegroundColor Yellow
        }
    }
}

if (!$isRemote) {
    # 0. Clean and start 3 local nodes
    & "$PSScriptRoot\stop-cluster.ps1"
    Start-Sleep -Seconds 1
    & "$PSScriptRoot\start-cluster.ps1" -Nodes 3 -BasePort 8000
}
else {
    Write-Host "Running in REMOTE / MULTI-PC mode (targeting running nodes).`n" -ForegroundColor Green
}

$passed = 0
$total = 4

function Assert-Condition($condition, $message) {
    if ($condition) {
        Write-Host "  [PASS] $message" -ForegroundColor Green
        return $true
    }
    else {
        Write-Host "  [FAIL] $message" -ForegroundColor Red
        return $false
    }
}

# ==============================================================================
# TEST 1: LOGICAL CLOCKS (Lamport & Vector Clocks, Total & Causal Ordering)
# ==============================================================================
Write-Host "`n----------------------------------------------------------------------" -ForegroundColor Yellow
Write-Host " TEST 1: Logical Clocks (Lamport & Vector Ordering) [25 Marks]" -ForegroundColor Yellow
Write-Host "----------------------------------------------------------------------" -ForegroundColor Yellow

# Broadcast message from Node 0
$msg0 = Invoke-RestMethod -Uri "$url0/api/broadcast" -Method Post -ContentType "application/json" -Body '{"text":"Alpha from Node 0"}'
Start-Sleep -Milliseconds 400

# Broadcast reply from Node 1 (causally after Node 0's message)
$msg1 = Invoke-RestMethod -Uri "$url1/api/broadcast" -Method Post -ContentType "application/json" -Body '{"text":"Beta from Node 1"}'
Start-Sleep -Milliseconds 400

# Broadcast from Node 2
$msg2 = Invoke-RestMethod -Uri "$url2/api/broadcast" -Method Post -ContentType "application/json" -Body '{"text":"Gamma from Node 2"}'
Start-Sleep -Milliseconds 600

# Fetch logs from all 3 nodes
$log0 = Invoke-RestMethod -Uri "$url0/api/messages"
$log1 = Invoke-RestMethod -Uri "$url1/api/messages"
$log2 = Invoke-RestMethod -Uri "$url2/api/messages"

$t1_ok = $true
$t1_ok = (Assert-Condition ($log0.Count -ge 3) "Node 0 received all 3 broadcast messages") -and $t1_ok
$t1_ok = (Assert-Condition ($log1.Count -ge 3) "Node 1 received all 3 broadcast messages") -and $t1_ok
$t1_ok = (Assert-Condition ($log2.Count -ge 3) "Node 2 received all 3 broadcast messages") -and $t1_ok

# Verify Lamport clock monotonically increases
$monotone = ($log0[0].lamport -le $log0[1].lamport) -and ($log0[1].lamport -le $log0[2].lamport)
$t1_ok = (Assert-Condition $monotone "Message log satisfies Lamport total order sorting") -and $t1_ok

# Verify logs match across nodes
$logsIdentical = ((ConvertTo-Json -Compress $log0) -eq (ConvertTo-Json -Compress $log1)) -and ((ConvertTo-Json -Compress $log1) -eq (ConvertTo-Json -Compress $log2))
$t1_ok = (Assert-Condition $logsIdentical "All nodes have identical, consistent message ordering") -and $t1_ok

Write-Host "`n  Message log output on Node 0:" -ForegroundColor Gray
foreach ($m in $log0) {
    Write-Host "    [L=$($m.lamport) N=$($m.sender_id)] vector=$(ConvertTo-Json -Compress $m.vector) -> $($m.text)" -ForegroundColor Gray
}

if ($t1_ok) { $passed++; Write-Host "`n-> TEST 1 PASSED (25/25 Marks)" -ForegroundColor Green }
else { Write-Host "`n-> TEST 1 FAILED" -ForegroundColor Red }


# ==============================================================================
# TEST 2: MUTUAL EXCLUSION & TOKEN RING (Shared Scoreboard Critical Section)
# ==============================================================================
Write-Host "`n----------------------------------------------------------------------" -ForegroundColor Yellow
Write-Host " TEST 2: Token Ring Mutual Exclusion & Shared Scoreboard [25 Marks]" -ForegroundColor Yellow
Write-Host "----------------------------------------------------------------------" -ForegroundColor Yellow

# Concurrently submit score updates to different nodes
Invoke-RestMethod -Uri "$url0/api/score" -Method Post -ContentType "application/json" -Body '{"player":"Alice","points":15}' | Out-Null
Invoke-RestMethod -Uri "$url1/api/score" -Method Post -ContentType "application/json" -Body '{"player":"Bob","points":25}' | Out-Null
Invoke-RestMethod -Uri "$url2/api/score" -Method Post -ContentType "application/json" -Body '{"player":"Alice","points":10}' | Out-Null

Write-Host "  Enqueued CS score updates on Node 0, Node 1, and Node 2. Awaiting token ring circulation..." -ForegroundColor Gray
Start-Sleep -Seconds 3

$sb0 = Invoke-RestMethod -Uri "$url0/api/scoreboard"
$sb1 = Invoke-RestMethod -Uri "$url1/api/scoreboard"
$sb2 = Invoke-RestMethod -Uri "$url2/api/scoreboard"

$t2_ok = $true
$t2_ok = (Assert-Condition ($sb0.Alice -ge 25) "Alice accumulated >= 25 points atomically in CS") -and $t2_ok
$t2_ok = (Assert-Condition ($sb0.Bob -ge 25) "Bob accumulated >= 25 points atomically in CS") -and $t2_ok
$sbMatch = ((ConvertTo-Json -Compress $sb0) -eq (ConvertTo-Json -Compress $sb1)) -and ((ConvertTo-Json -Compress $sb1) -eq (ConvertTo-Json -Compress $sb2))
$t2_ok = (Assert-Condition $sbMatch "Shared scoreboard converged identically across all nodes without race conditions") -and $t2_ok

Write-Host "`n  Final Scoreboard:" -ForegroundColor Gray
foreach ($prop in $sb0.PSObject.Properties) {
    Write-Host "    $($prop.Name) = $($prop.Value) points" -ForegroundColor Gray
}

if ($t2_ok) { $passed++; Write-Host "`n-> TEST 2 PASSED (25/25 Marks)" -ForegroundColor Green }
else { Write-Host "`n-> TEST 2 FAILED" -ForegroundColor Red }


# ==============================================================================
# TEST 3: BULLY LEADER ELECTION (Host Failure Detection & New Coordinator)
# ==============================================================================
Write-Host "`n----------------------------------------------------------------------" -ForegroundColor Yellow
Write-Host " TEST 3: Bully Leader Election & Leader Failure Recovery [25 Marks]" -ForegroundColor Yellow
Write-Host "----------------------------------------------------------------------" -ForegroundColor Yellow

$statInitial = Invoke-RestMethod -Uri "$url0/api/status"
$t3_ok = Assert-Condition ($statInitial.leader -eq 2 -or $statInitial.leader -eq 1) "Initial cluster leader is identified"

if (!$isRemote) {
    Write-Host "  Simulating crash of Leader (Node 2, port 8002)..." -ForegroundColor Yellow
    $proc2 = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -match "Node 2" }
    if ($proc2) {
        Stop-Process -Id $proc2.ProcessId -Force
        Write-Host "  Node 2 PID $($proc2.ProcessId) terminated." -ForegroundColor Gray
    }
    Write-Host "  Waiting for health monitor detection and Bully election convergence..." -ForegroundColor Gray
    Start-Sleep -Seconds 5

    $stat0 = Invoke-RestMethod -Uri "$url0/api/status"
    $stat1 = Invoke-RestMethod -Uri "$url1/api/status"

    $t3_ok = (Assert-Condition ($stat1.is_leader -eq $true) "Node 1 successfully declared itself new LEADER") -and $t3_ok
    $t3_ok = (Assert-Condition ($stat0.leader -eq 1) "Node 0 recognized Node 1 as the new COORDINATOR") -and $t3_ok
    $t3_ok = (Assert-Condition ($stat1.leader -eq 1) "Node 1 is recognized as leader cluster-wide") -and $t3_ok
}
else {
    Write-Host "  Remote Mode: Triggering Bully Election wave from Node 0..." -ForegroundColor Cyan
    Invoke-RestMethod -Uri "$url0/api/trigger-election" -Method Post | Out-Null
    Start-Sleep -Seconds 3

    $stat0 = Invoke-RestMethod -Uri "$url0/api/status"
    $t3_ok = (Assert-Condition ($stat0.leader -ge 0) "Bully election successfully coordinated leader (Node $($stat0.leader))") -and $t3_ok
}

if ($t3_ok) { $passed++; Write-Host "`n-> TEST 3 PASSED (25/25 Marks)" -ForegroundColor Green }
else { Write-Host "`n-> TEST 3 FAILED" -ForegroundColor Red }


# ==============================================================================
# TEST 4: TOKEN RING RESILIENCE (Critical Section Operation)
# ==============================================================================
Write-Host "`n----------------------------------------------------------------------" -ForegroundColor Yellow
Write-Host " TEST 4: Token Ring Crash Resilience & Scoreboard Continuity [25 Marks]" -ForegroundColor Yellow
Write-Host "----------------------------------------------------------------------" -ForegroundColor Yellow

Invoke-RestMethod -Uri "$url0/api/score" -Method Post -ContentType "application/json" -Body '{"player":"Charlie","points":50}' | Out-Null
Write-Host "  Enqueued score update for 'Charlie' on Node 0..." -ForegroundColor Gray

Start-Sleep -Seconds 3

$sbSurviving = Invoke-RestMethod -Uri "$url1/api/scoreboard"
$t4_ok = Assert-Condition ($sbSurviving.Charlie -ge 50) "Charlie's score (50 pts) successfully propagated across surviving ring"

if ($t4_ok) { $passed++; Write-Host "`n-> TEST 4 PASSED (25/25 Marks)" -ForegroundColor Green }
else { Write-Host "`n-> TEST 4 FAILED" -ForegroundColor Red }


# ==============================================================================
# SUMMARY REPORT
# ==============================================================================
Write-Host "`n======================================================================" -ForegroundColor Cyan
Write-Host " TEST RUN SUMMARY: $passed / $total Scenarios Passed ($($passed * 25) / 100 Marks)" -ForegroundColor Cyan
Write-Host "======================================================================`n" -ForegroundColor Cyan

# Cleanup
if (!$isRemote) {
    & "$PSScriptRoot\stop-cluster.ps1"
}
else {
    Write-Host "Remote test complete. Nodes remain active on their respective machines.`n" -ForegroundColor Green
}
