# MCP 迁移：SSE Proxy → STDIO 直连

## 动机

当前 MCP 通信链路：
  Agent → Spring AI MCP Client → SSE(:3000) → mcp-proxy → jchatmind-shell-mcp.mjs

问题：
1. 多一个 mcp-proxy 子进程，增加部署复杂度
2. 启动顺序依赖（先启 proxy 再启后端）
3. 不能轻松添加更多 MCP 服务器

## 目标架构

  Agent → Spring AI MCP Client → STDIO → jchatmind-shell-mcp.mjs（直接子进程）
                               → STDIO → puppeteer（更多 MCP 服务器）

## 改动

| 文件 | 变更 |
|------|------|
| application.yaml | SSE 配置 → STDIO 配置 |
| mcp-servers-windows.json | 路径改为相对路径 |
| McpProxyRuntime.java | 移除（不再需要管理 proxy） |
| McpProxyLauncher.java | 移除 |