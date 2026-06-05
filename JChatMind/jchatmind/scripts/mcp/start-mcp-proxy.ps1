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
Write-Host "Starting mcp-proxy on :3000 with jchatmind-shell-mcp"
npx -y mcp-proxy --port 3000 --server sse -- node $server
