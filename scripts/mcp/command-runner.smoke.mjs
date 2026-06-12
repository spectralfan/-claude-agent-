/**
 * 启动自检用例：不依赖本机 workspace/tank-battle 等路径，仅验证 command-runner 规范化逻辑。
 * 完整集成测试见 command-runner.test.mjs（本地手动 node --test）。
 */
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import {
  normalizeCommand,
  rewriteNodeCheck,
  rewriteUnsafeCmdPipelines,
  runCommand,
  splitByAnd,
  translateForPlatform,
  RUNNER_VERSION,
} from "./command-runner.mjs";

const workspace = fs.mkdtempSync(path.join(os.tmpdir(), "jchatmind-smoke-"));

describe("smoke: runner version", () => {
  it("exports RUNNER_VERSION", () => {
    assert.match(RUNNER_VERSION, /^\d+\.\d+\.\d+$/);
  });
});

describe("smoke: normalizeCommand", () => {
  const cwd = workspace;

  it("relativizes quoted absolute dir on Windows", () => {
    const abs = path.join(cwd, "js", "game.js").replace(/\//g, "\\");
    const raw = `dir "${abs}"`;
    const { prepared } = normalizeCommand(raw, cwd, { platform: "win32" });
    assert.match(prepared, /js[\\/]game\.js/);
  });

  it("rewrites node --check html to existence check", () => {
    const html = path.join(cwd, "demo.html");
    fs.writeFileSync(html, "<html></html>");
    const raw = `node --check "${html}"`;
    const { prepared } = normalizeCommand(raw, cwd, { platform: "win32" });
    assert.match(prepared, /demo\.html/);
    assert.doesNotMatch(prepared, /^node --check/i);
  });

  it("translates mkdir -p to PowerShell", () => {
    const { prepared } = normalizeCommand("mkdir -p lib", cwd, { platform: "win32" });
    assert.match(prepared, /New-Item -ItemType Directory/);
  });
});

describe("smoke: rewrite helpers", () => {
  it("splitByAnd respects quotes", () => {
    assert.deepEqual(splitByAnd('echo "a&&b" && dir x'), ['echo "a&&b"', "dir x"]);
  });

  it("rewriteNodeCheck keeps js relative", () => {
    const abs = path.join(workspace, "a.js");
    const out = rewriteNodeCheck(`node --check "${abs}"`, workspace, true);
    assert.equal(out, `node --check a.js`);
  });

  it("rewriteUnsafeCmdPipelines handles html pipe find", () => {
    const raw = 'type demo.html | find "</script>" >nul && echo OK';
    const out = rewriteUnsafeCmdPipelines(raw, workspace, true);
    assert.match(out, /Get-Content/);
    assert.doesNotMatch(out, /\|/);
  });
});

describe("smoke: runCommand", () => {
  it("runs dir in temp workspace via PowerShell on Windows", async () => {
    const probe = path.join(workspace, "probe.txt");
    fs.writeFileSync(probe, "ok");
    const r = await runCommand("dir probe.txt", workspace, { platform: "win32" });
    assert.equal(r.exitCode, 0);
    assert.equal(r.executor, "powershell");
    assert.match(r.stdout, /probe\.txt/i);
  });
});

describe("smoke: translateForPlatform", () => {
  it("maps type to cat on POSIX", () => {
    assert.equal(translateForPlatform("type readme.md", false), "cat readme.md");
  });
});
