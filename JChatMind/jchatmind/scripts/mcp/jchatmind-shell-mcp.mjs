#!/usr/bin/env node
/**
 * JChatMind MCP shell：单工具 execute_command。
 * 跨平台执行委托给 command-runner.mjs（PowerShell@Windows / sh@POSIX）。
 */
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";
import path from "node:path";
import fs from "node:fs";
import {
  formatResult,
  isWindowsPlatform,
  RUNNER_VERSION,
  runCommand,
  unwrapCommandIfJson,
} from "./command-runner.mjs";

const server = new Server(
  { name: "jchatmind-shell-mcp", version: "2.3.1" },
  { capabilities: { tools: {} } }
);

const PLATFORM_HINT = isWindowsPlatform()
  ? "Windows: commands run via PowerShell; Unix shims (mkdir -p, touch, ls, cp, rm) auto-translated. Set workingDir, prefer relative paths."
  : "POSIX: commands run via sh; Windows cmd shims (dir, type, copy, md) auto-translated. Set workingDir, prefer relative paths.";

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [
    {
      name: "execute_command",
      description:
        "Execute a shell command in workingDir. " + PLATFORM_HINT +
        " JS syntax: node --check path.js. File read checks auto-translated. node -e runs without shell quoting.",
      inputSchema: {
        type: "object",
        properties: {
          command: { type: "string", description: "Command line to run" },
          workingDir: {
            type: "string",
            description: "Working directory (recommended)",
          },
          shell: {
            type: "string",
            description: "Ignored; platform shell is chosen automatically",
          },
        },
        required: ["command"],
      },
    },
  ],
}));

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  if (request.params.name !== "execute_command") {
    throw new Error(`Unknown tool: ${request.params.name}`);
  }
  const args = request.params.arguments ?? {};
  const command = unwrapCommandIfJson(args.command ?? "");
  if (!command) {
    return {
      content: [{ type: "text", text: formatResult("Error: command is required", 1) }],
      isError: true,
    };
  }
  const cwd = args.workingDir ? path.resolve(String(args.workingDir)) : process.cwd();
  if (args.workingDir && !fs.existsSync(cwd)) {
    return {
      content: [{
        type: "text",
        text: formatResult(`Error: workingDir does not exist: ${cwd}`, 1),
      }],
      isError: true,
    };
  }

  const result = await runCommand(command, cwd);
  const text = [result.stdout, result.stderr ? `stderr:\n${result.stderr}` : "", result.message]
    .filter(Boolean)
    .join("\n")
    .trim();
  const meta = [
    `[runner: ${RUNNER_VERSION}]`,
    result.prepared !== command ? `[normalized: ${result.prepared}]` : "",
  ].filter(Boolean).join(" ");
  const metaSuffix = meta ? `\n${meta}` : "";

  if (result.exitCode !== 0) {
    return {
      content: [{ type: "text", text: formatResult((text || result.message || "command failed") + metaSuffix, 1) }],
      isError: true,
    };
  }
  return {
    content: [{ type: "text", text: formatResult(text + metaSuffix, 0) }],
  };
});

const transport = new StdioServerTransport();
await server.connect(transport);
