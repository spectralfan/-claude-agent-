# Orchestrator + Worker E2E: tank battle mini game in default workspace
# Prereq: backend running on :8080 with MCP enabled

$ErrorActionPreference = "Stop"
$BaseUrl = "http://localhost:8080/api"
$WorkspacePath = "Z:/JAVA_workshop/JChatMindv2/workspace"
$UserPrompt = "Use JavaScript to build a tank battle mini game. Create index.html, game.js, and style.css playable in browser. Delegate coding work to worker via delegate_coding_task."
$PollSeconds = 600
$PollInterval = 15

function Invoke-Api($Method, $Path, $Body = $null) {
    $params = @{
        Uri         = "$BaseUrl$Path"
        Method      = $Method
        ContentType = "application/json; charset=utf-8"
    }
    if ($Body) { $params.Body = ($Body | ConvertTo-Json -Depth 8 -Compress) }
    $resp = Invoke-RestMethod @params
    if ($resp.code -ne 200) { throw "API $Path failed: $($resp.message)" }
    return $resp.data
}

Write-Host "== 1. runtime-status =="
$rt = Invoke-Api GET "/coding/runtime-status"
Write-Host "mcpEnabled=$($rt.mcpEnabled) springMcp=$($rt.springMcpClientEnabled)"

Write-Host "== 2. orchestrator preset =="
$orch = Invoke-Api GET "/coding/agents/orchestrator-preset"
if (-not $orch) { throw "orchestrator preset missing" }
$orchAgentId = $orch.agentId
Write-Host "orchestrator=$orchAgentId"

Write-Host "== 3. workspace =="
$wsRoot = $WorkspacePath
Write-Host "workspaceRoot=$wsRoot"

Write-Host "== 4. create chat session =="
$session = Invoke-Api POST "/chat-sessions" @{ agentId = $orchAgentId; title = "E2E tank game" }
$sessionId = $session.chatSessionId
Write-Host "sessionId=$sessionId"

Write-Host "== 5. create coding task =="
$task = Invoke-Api POST "/coding/tasks" @{
    sessionId     = $sessionId
    agentId       = $orchAgentId
    workspaceRoot = $wsRoot
    workspacePath = "."
    approvalMode  = "development"
}
$taskId = $task.id
Write-Host "taskId=$taskId"

Write-Host "== 6. send user message =="
Invoke-Api POST "/chat-messages" @{
    agentId   = $orchAgentId
    sessionId = $sessionId
    role      = "user"
    content   = $UserPrompt
} | Out-Null
Write-Host "message sent, polling subtasks up to ${PollSeconds}s ..."

$deadline = (Get-Date).AddSeconds($PollSeconds)
$stableCompletedPolls = 0
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds $PollInterval
    $subs = Invoke-Api GET "/coding/tasks/session/$sessionId/subtasks"
    if (-not $subs -or $subs.Count -eq 0) {
        Write-Host "  waiting for subtasks..."
        $stableCompletedPolls = 0
        continue
    }
    foreach ($s in $subs) {
        Write-Host "  subtask $($s.id) status=$($s.status) title=$($s.title)"
        if ($s.status -eq "FAILED") {
            throw "subtask failed: $($s.errorMessage)"
        }
    }
    $running = ($subs | Where-Object { $_.status -in @("RUNNING", "PENDING") }).Count
    $completed = ($subs | Where-Object { $_.status -eq "COMPLETED" }).Count
    if ($running -eq 0 -and $completed -gt 0) {
        $stableCompletedPolls++
        if ($stableCompletedPolls -ge 2) {
            Write-Host "== 7. subtasks stable COMPLETED (no RUNNING) =="
            break
        }
    } else {
        $stableCompletedPolls = 0
    }
}

if ((Get-Date) -ge $deadline) { throw "timeout waiting for subtasks" }

Write-Host "== 8. check workspace artifacts =="
$wsDir = $WorkspacePath -replace '/', '\'
$files = @("index.html", "game.js", "style.css")
$found = 0
foreach ($f in $files) {
    $p = Join-Path $wsDir $f
    if (Test-Path $p) {
        $len = (Get-Item $p).Length
        Write-Host "  OK $p size=$len"
        $found++
    } else {
        Write-Host "  MISS $p"
    }
}

Write-Host "== E2E DONE session=$sessionId task=$taskId artifacts=$found/$($files.Count) =="
