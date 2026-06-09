# 从 Windows 用户/系统环境变量注入 DEEPSEEK_API_KEY 后启动 Spring Boot。
# 解决 IDE/Cursor 子进程未继承系统环境变量导致 401 的问题。
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

function Resolve-EnvKey {
    param([string]$Name)
    $processVal = [System.Environment]::GetEnvironmentVariable($Name, "Process")
    if ($processVal) { return @{ Value = $processVal; Source = "process" } }
    foreach ($scope in @("User", "Machine")) {
        $v = [System.Environment]::GetEnvironmentVariable($Name, $scope)
        if ($v) { return @{ Value = $v; Source = $scope } }
    }
    return $null
}

foreach ($name in @("DEEPSEEK_API_KEY", "ZHIPU_API_KEY")) {
    $resolved = Resolve-EnvKey -Name $name
    if ($resolved) {
        Set-Item -Path "Env:$name" -Value $resolved.Value
        Write-Host "$name loaded from $($resolved.Source) (length=$($resolved.Value.Length))"
    } else {
        Write-Host "$name not set in process/User/Machine; use application-local.yaml if needed."
    }
}

if (-not $env:DEEPSEEK_API_KEY) {
    Write-Warning @"
DEEPSEEK_API_KEY 仍未注入。聊天请求将出现 HTTP 401。
可选方案：
  1. 在 Windows「系统属性 → 环境变量」设置 DEEPSEEK_API_KEY 后完全重启 IDE
  2. 复制 application-local.yaml.example 为 application-local.yaml 并填写 api-key
  3. 当前终端临时设置: `$env:DEEPSEEK_API_KEY = 'sk-...'
"@
}

Push-Location $root
try {
    if ($args.Count -gt 0) {
        & mvn @args
    } else {
        & mvn spring-boot:run
    }
} finally {
    Pop-Location
}
