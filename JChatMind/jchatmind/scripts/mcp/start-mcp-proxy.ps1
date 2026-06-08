# JChatMind 内置 Windows MCP shell + SSE 代理（spring.ai.mcp.client → localhost:3000）
$ErrorActionPreference = "Stop"
$dir = $PSScriptRoot
$server = Join-Path $dir "jchatmind-shell-mcp.mjs"
if (-not (Test-Path $server)) {
    throw "MCP server not found: $server"
}
$uvBin = Join-Path $env:USERPROFILE ".local\bin"
if (Test-Path $uvBin) {
    $env:Path = "$uvBin;$env:Path"
}
Push-Location $dir
if (-not (Test-Path "node_modules")) {
    Write-Host "Installing jchatmind-mcp-shell dependencies..."
    npm install --silent
}
Pop-Location
$env:JCHATMIND_MCP_PLATFORM = "windows"
$env:JCHATMIND_MCP_EXECUTOR = "powershell"
$env:JCHATMIND_PREVIEW_PORT = "5500"
$env:JCHATMIND_RESERVED_PORTS = "8080,3000,5173"
Write-Host "Starting mcp-proxy on :3000 with jchatmind-shell-mcp (platform=windows, executor=powershell)"
npx -y mcp-proxy --port 3000 --server sse -- node $server
