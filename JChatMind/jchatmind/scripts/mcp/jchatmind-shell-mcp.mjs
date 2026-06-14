#!/usr/bin/env node
/**
 * JChatMind MCP shell — pure bash, no PowerShell baggage.
 * Uses Git Bash on Windows, sh on POSIX.
 */
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";
import { execFile } from "node:child_process";

const MAX_BYTES = 64 * 1024;
const TIMEOUT_MS = 120_000;
const bash = process.env.JCHATMIND_BASH_PATH || (process.platform === "win32" ? "bash" : "sh");

const server = new Server(
  { name: "jchatmind-shell-mcp", version: "3.0.0" },
  { capabilities: { tools: {} } },
);

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [{
    name: "execute_command",
    description:
      "Execute a shell command. Use bash syntax (ls, cat, pwd, find, grep, mkdir, node, python, git, etc). "
      + "Output is truncated at 64 KB. Timeout after 120s.",
    inputSchema: {
      type: "object",
      properties: {
        command: { type: "string", description: "Shell command to run (bash syntax)" },
        workingDir: { type: "string", description: "Working directory (optional)" },
      },
      required: ["command"],
    },
  }],
}));

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  if (request.params.name !== "execute_command") {
    throw new Error("Unknown tool: " + request.params.name);
  }
  const args = request.params.arguments ?? {};
  // normalize Windows backslashes to forward slashes for Git Bash
  const command = String(args.command ?? "").trim().replace(/\\/g, '/');
  if (!command) return { content: [{ type: "text", text: "Error: command is required" }], isError: true };

  const env = { ...process.env };
  if (args.workingDir) env.PWD = String(args.workingDir);

  return new Promise((resolve) => {
    const child = execFile(bash, ["-c", command], {
      cwd: args.workingDir ? String(args.workingDir) : process.cwd(),
      env,
      maxBuffer: MAX_BYTES,
      timeout: TIMEOUT_MS,
    }, (err, stdout, stderr) => {
      const out = (stdout || "") + (stderr ? "\nstderr:\n" + stderr : "");
      const truncated = out.length > MAX_BYTES;
      const text = truncated ? out.slice(0, MAX_BYTES) + "\n[truncated]" : out || "[no output]";
      const exitCode = err ? (err.code === "ETIMEDOUT" ? 124 : err.code || 1) : 0;
      resolve({
        content: [{ type: "text", text: text + "\nexit code: " + exitCode }],
        isError: exitCode !== 0,
      });
    });
  });
});

const transport = new StdioServerTransport();
await server.connect(transport);