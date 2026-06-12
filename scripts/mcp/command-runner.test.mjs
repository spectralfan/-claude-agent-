import { describe, it } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import {
  normalizeCommand,
  rewriteNodeCheck,
  rewriteUnsafeCmdPipelines,
  runCommand,
  splitByAnd,
  translateForPlatform,
} from "./command-runner.mjs";

const execFileAsync = promisify(execFile);
const cwd = path.resolve("Z:/JAVA_workshop/JChatMindv2/workspace");

describe("normalizeCommand", () => {
  it("strips redundant cd /d when workingDir matches", () => {
    const raw = 'cd /d "Z:\\JAVA_workshop\\JChatMindv2\\workspace" && dir js\\game.js';
    const { prepared } = normalizeCommand(raw, cwd, { platform: "win32" });
    assert.match(prepared, /js[\\/]game\.js/);
  });

  it("rewrites quoted absolute dir to relative on Windows", () => {
    const raw = 'dir "Z:\\JAVA_workshop\\JChatMindv2\\workspace\\js\\game.js"';
    const { prepared } = normalizeCommand(raw, cwd, { platform: "win32" });
    assert.match(prepared, /js[\\/]game\.js/);
  });

  it("rewrites js readFileSync syntax check to node --check", () => {
    const raw = `node -e "const fs=require('fs');new Function(fs.readFileSync('js/game.js','utf8'))"`;
    const { prepared } = normalizeCommand(raw, cwd, { platform: "win32" });
    assert.equal(prepared, "node --check js\\game.js");
  });

  it("rewrites index.html readFileSync to file verify on Windows", () => {
    const raw =
      `node -e "require('fs').readFileSync('index.html','utf8'); console.log('index.html 读取成功');"`;
    const { prepared } = normalizeCommand(raw, cwd, { platform: "win32" });
    assert.match(prepared, /Test-Path.*index\.html/i);
    assert.match(prepared, /index\.html 读取成功/);
  });

  it("translates mkdir -p to PowerShell on Windows", () => {
    const { prepared } = normalizeCommand("mkdir -p js/lib", cwd, { platform: "win32" });
    assert.match(prepared, /New-Item -ItemType Directory -Force/);
  });

  it("rewrites esm import node -e to node --check chain", () => {
    const raw = `cd tank-battle && node --input-type=module -e "
import { CANVAS_WIDTH } from './js/constants.js';
import { Tank } from './js/tank.js';
console.log('ok');
"`;
    const { prepared, steps } = normalizeCommand(raw, cwd, { platform: "win32" });
    assert.match(prepared, /node --check tank-battle/);
    assert.match(prepared, /constants\.js/);
    assert.match(prepared, /tank\.js/);
    assert.ok(steps.some((s) => s.step === "rewriteNodeEval"));
  });

  it("rewrites http-server on 8080 to html existence check", () => {
    const raw =
      "cd Z:\\JAVA_workshop\\JChatMindv2\\workspace && start http://localhost:8080 && npx -y http-server -p 8080 --cors -c-1";
    const { prepared, steps } = normalizeCommand(raw, cwd, { platform: "win32" });
    assert.match(prepared, /Test-Path.*index\.html/i);
    assert.ok(steps.some((s) => s.step === "rewritePreviewServer"));
  });

  it("translates dir to ls on POSIX", () => {
    const { prepared } = normalizeCommand("dir /b js", cwd, { platform: "posix" });
    assert.equal(prepared, "ls js");
  });
});

describe("splitByAnd", () => {
  it("respects quotes", () => {
    const parts = splitByAnd('echo "a&&b" && dir js');
    assert.deepEqual(parts, ['echo "a&&b"', "dir js"]);
  });
});

function workspaceHas(rel) {
  return fs.existsSync(path.join(cwd, rel));
}

describe("runCommand integration", () => {
  it("runs dir on Windows via PowerShell", async () => {
    if (!workspaceHas("tank-battle\\index.html")) return;
    const r = await runCommand("dir tank-battle\\index.html", cwd, { platform: "win32" });
    assert.equal(r.exitCode, 0);
    assert.match(r.stdout, /index\.html/i);
  });

  it("runs previously failing quoted absolute dir on Windows", async () => {
    if (!workspaceHas("tank-battle\\index.html")) return;
    const raw = 'dir "Z:\\JAVA_workshop\\JChatMindv2\\workspace\\tank-battle\\index.html"';
    const r = await runCommand(raw, cwd, { platform: "win32" });
    assert.equal(r.exitCode, 0);
  });

  it("runs cd && chain on Windows", async () => {
    if (!workspaceHas("tank-battle")) return;
    const raw = 'cd /d "Z:\\JAVA_workshop\\JChatMindv2\\workspace" && dir /b';
    const r = await runCommand(raw, cwd, { platform: "win32" });
    assert.equal(r.exitCode, 0);
    assert.match(r.stdout, /tank-battle/i);
  });

  it("runs http-server 8080 command as preview check on Windows", async () => {
    if (!workspaceHas("tank-battle")) return;
    const raw =
      "cd tank-battle && npx -y http-server -p 8080";
    const r = await runCommand(raw, cwd, { platform: "win32" });
    assert.equal(r.exitCode, 0);
    assert.match(r.stdout, /index\.html|5500|8080/);
  });

  it("runs index.html node -e read via rewrite on Windows", async () => {
    if (!workspaceHas("tank-battle\\index.html")) return;
    const raw =
      `node -e "require('fs').readFileSync('tank-battle/index.html','utf8'); console.log('index.html 读取成功');"`;
    const r = await runCommand(raw, cwd, { platform: "win32" });
    assert.equal(r.exitCode, 0);
    assert.match(r.stdout, /读取成功|index\.html/i);
    assert.doesNotMatch(r.stderr || "", /cmd\.exe/i);
  });

  it("runs node -e direct without shell quoting", async () => {
    try {
      await execFileAsync("where", ["node"], { shell: true });
    } catch {
      return;
    }
    const raw = `node -e "console.log('direct-ok')"`;
    const r = await runCommand(raw, cwd, { platform: "win32" });
    assert.equal(r.exitCode, 0);
    assert.match(r.stdout, /direct-ok/);
    assert.match(r.executed.join(" "), /direct/);
  });

  it("runs node --check html as file existence on Windows", async () => {
    if (!workspaceHas("tank-battle.html")) return;
    const raw =
      'node --check "Z:\\JAVA_workshop\\JChatMindv2\\workspace\\tank-battle.html"';
    const r = await runCommand(raw, cwd, { platform: "win32" });
    assert.equal(r.exitCode, 0);
    assert.match(r.stdout, /tank-battle\.html/);
    assert.equal(r.executor, "powershell");
    assert.doesNotMatch((r.message || "") + (r.stderr || ""), /cmd\.exe/i);
  });

  it("runs node --check on Windows", async () => {
    try {
      await execFileAsync("where", ["node"], { shell: true });
    } catch {
      return; // skip if node not in PATH in CI
    }
    const r = await runCommand("node --check game.js", cwd, { platform: "win32" });
    // game.js 为游戏源码，可能有语法边界；此处只验证命令可达
    assert.ok(r.exitCode === 0 || r.exitCode === 1);
    assert.match(r.executed.join(" "), /node --check/);
  });
});

describe("rewriteNodeCheck", () => {
  it("relativizes absolute path and rewrites html to existence check", () => {
    const raw =
      'node --check "Z:\\JAVA_workshop\\JChatMindv2\\workspace\\tank-battle.html"';
    const out = rewriteNodeCheck(raw, cwd, true);
    assert.match(out, /Test-Path/);
    assert.match(out, /tank-battle\.html/);
    assert.doesNotMatch(out, /^node --check/i);
  });

  it("keeps js node --check as relative", () => {
    const raw = 'node --check "Z:\\JAVA_workshop\\JChatMindv2\\workspace\\js\\game.js"';
    const out = rewriteNodeCheck(raw, cwd, true);
    assert.equal(out, "node --check js\\game.js");
  });
});

describe("rewriteUnsafeCmdPipelines", () => {
  it("rewrites html script pipe find to PowerShell", () => {
    const raw =
      'cd /d "Z:\\JAVA_workshop\\JChatMindv2\\workspace" & type tank-battle.html | find "</script>" >nul && echo HTML_SCRIPT_TAG_OK';
    const out = rewriteUnsafeCmdPipelines(raw, cwd, true);
    assert.match(out, /Get-Content/);
    assert.match(out, /HTML_SCRIPT_TAG_OK/);
    assert.doesNotMatch(out, /\|/);
  });
});

describe("translateForPlatform", () => {
  it("maps touch to PowerShell", () => {
    const out = translateForPlatform("touch foo.txt", true);
    assert.match(out, /New-Item -ItemType File/);
  });

  it("maps type to cat on POSIX", () => {
    const out = translateForPlatform("type readme.md", false);
    assert.equal(out, "cat readme.md");
  });
});
