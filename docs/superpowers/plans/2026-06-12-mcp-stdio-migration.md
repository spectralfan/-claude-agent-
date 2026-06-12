# 执行计划：MCP STDIO 迁移 + 新工具接入

## 已完成
- [x] 设计文档: docs/superpowers/specs/2026-06-12-mcp-stdio-migration-design.md
- [x] application.yaml: SSE → STDIO 配置切换
- [x] mcp-servers-windows.json: 相对路径
- [x] McpClientManager: 启动时 STDIO 连接+运行时查询
- [x] McpToolBridgeImpl: 委托给 Manager
- [x] WeatherQueryTool: 天气查询（Java-native）
- [x] agent-profiles/worker.yaml: 加入 weather 工具
- [x] proxy auto-start: false

## E2E 验证日志
StdioClientTransport: MCP server starting ✅
StdioClientTransport: MCP server started ✅
MCP client[jchatmind-shell-mcp] discovered 1 tools: [execute_command] ✅
McpClientManager initialized with 1 MCP clients, 1 tools ✅
Tomcat started on port 8080 ✅