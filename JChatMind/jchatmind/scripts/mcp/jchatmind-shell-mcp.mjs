#!/usr/bin/env node
/**
 * Windows 友好 MCP shell：单工具 execute_command，经 cmd.exe 执行，支持 workingDir。
 * 供 mcp-proxy SSE 桥接，替代 bash 包装的 mkusaka / 超时较重的第三方 server。
 */
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import path from "node:path";

const execFileAsync = promisify(execFile);
const isWin = process.platform === "win32";
const DEFAULT_TIMEOUT_MS = 300_000;

const server = new Server(
  { name: "jchatmind-shell-mcp", version: "1.0.0" },
  { capabilities: { tools: {} } }
);

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [
    {
      name: "execute_command",
      description:
        "Execute a shell command. On Windows uses cmd.exe. Optional workingDir.",
      inputSchema: {
        type: "object",
        properties: {
          command: { type: "string", description: "Command line to run" },
          workingDir: {
            type: "string",
            description: "Working directory (optional)",
          },
          shell: {
            type: "string",
            description: "Ignored on Windows; use cmd",
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
  const command = String(args.command ?? "").trim();
  if (!command) {
    return {
      content: [{ type: "text", text: "Error: command is required" }],
      isError: true,
    };
  }
  const cwd = args.workingDir ? path.resolve(String(args.workingDir)) : process.cwd();
  try {
    let stdout = "";
    let stderr = "";
    if (isWin) {
      const result = await execFileAsync("cmd.exe", ["/c", command], {
        cwd,
        timeout: DEFAULT_TIMEOUT_MS,
        maxBuffer: 10 * 1024 * 1024,
        windowsHide: true,
        env: process.env,
      });
      stdout = result.stdout ?? "";
      stderr = result.stderr ?? "";
    } else {
      const result = await execFileAsync("sh", ["-c", command], {
        cwd,
        timeout: DEFAULT_TIMEOUT_MS,
        maxBuffer: 10 * 1024 * 1024,
        env: process.env,
      });
      stdout = result.stdout ?? "";
      stderr = result.stderr ?? "";
    }
    const text = [stdout, stderr ? `stderr:\n${stderr}` : ""]
      .filter(Boolean)
      .join("\n")
      .trim();
    return {
      content: [{ type: "text", text: text || "(no output)" }],
    };
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    const out = err.stdout ? String(err.stdout) : "";
    const errOut = err.stderr ? String(err.stderr) : "";
    const text = [out, errOut, message].filter(Boolean).join("\n").trim();
    return {
      content: [{ type: "text", text: text || message }],
      isError: true,
    };
  }
});

const transport = new StdioServerTransport();
await server.connect(transport);
