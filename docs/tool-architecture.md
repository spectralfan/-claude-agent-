# Tool Architecture

JChatMind tools come from two systems, unified as ToolCallback for the Agent.

## 1. Java Native Tools

### Registration
- @Component + implements Tool interface
- Method needs @Tool annotation (required by Spring AI MethodToolCallbackProvider)
- Auto-collected by ToolFacadeService
- Compiled with the backend

### Injection condition
Agent allowedTools must contain the tool name.

### Current inventory

| Tool Name | Type | Purpose | Agent |
|-----------|------|---------|-------|
| directAnswer | FIXED | Direct reply | All |
| terminate | FIXED | Stop loop | All |
| coding_file_tools | FIXED | Workspace file ops | Worker |
| coding_search_tools | FIXED | Code search | Worker |
| coding_verify_tools | FIXED | Code verify | Worker |
| coding_run_tool | FIXED | Code execute | Worker |
| orchestration_task_tools | FIXED | DAG orchestration | Scheduler |
| orchestration_read_tools | FIXED | DAG read-only | Scheduler/Reviewer |
| orchestration_shell_tools | FIXED | Shell execute | Worker |
| dataBaseTool | OPTIONAL | DB query | Custom |
| fileSystemTool | OPTIONAL | File I/O | Custom |
| KnowledgeTool | OPTIONAL | RAG search | All |
| git_tool | OPTIONAL | Git management | Worker |
| save_note | OPTIONAL | Persist facts | Worker/Scheduler |
| cityTool | OPTIONAL | City query (demo) | Demo |
| dateTool | OPTIONAL | Date query (demo) | Demo |
| weatherTool | OPTIONAL | Weather query (demo) | Demo |

## 2. MCP Tools

### Registration
- MCP protocol STDIO sub-process
- Configure in mcp-servers-windows.json
- Auto-discovered by McpClientManager on startup
- No recompile needed

### Current inventory

| Source | Tool Name | Description |
|--------|-----------|-------------|
| jchatmind-shell-mcp.mjs | execute_command | Shell execution (aliases: bash, shell, run_terminal_cmd) |

## 3. Injection Flow

Agent sends message -> JChatMindFactory.create()
  -> resolveRuntimeTools() -> read DB allowedTools -> match Java @Component -> inject
  -> injectMcpToolCallbacks() -> query McpClientManager -> match allowedTools -> inject
  -> all ToolCallbacks merged -> AgentLoop.run()

## Key Rules
1. Java native tools MUST have @Tool annotation on their methods
2. MCP tools are auto-discovered, no annotation needed
3. Both need the tool name in agent allowedTools to be injected
4. FIXED tools are always injected, OPTIONAL tools need allowedTools config