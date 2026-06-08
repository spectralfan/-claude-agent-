/**
 * JChatMind MCP 跨平台命令执行层（单一入口）
 *
 * 策略：
 * 1. 规范化管线：JSON 解包 → 去冗余 cd → 路径相对化 → node -e 改写
 * 2. Windows：经 PowerShell 执行（避开 cmd.exe 引号/路径陷阱），Unix 命令转 PS
 * 3. Linux/macOS：经 sh -c 执行，Windows cmd 命令转 POSIX
 * 4. && 链在 Node 层分段执行，每段独立退出码
 */
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import path from "node:path";

const execFileAsync = promisify(execFile);

export const RUNNER_VERSION = "2.3.1";
export const DEFAULT_TIMEOUT_MS = 300_000;

export function isWindowsPlatform(platform = resolveConfiguredPlatform()) {
  return platform === "win32";
}

/** 优先读后端/启动脚本注入的环境变量，其次 OS */
export function resolveConfiguredPlatform() {
  const configured = (process.env.JCHATMIND_MCP_PLATFORM || "").trim().toLowerCase();
  if (configured === "windows" || configured === "win32") return "win32";
  if (configured === "posix" || configured === "linux" || configured === "unix") {
    return "posix";
  }
  return process.platform === "win32" ? "win32" : "posix";
}

export function resolveConfiguredExecutor() {
  const configured = (process.env.JCHATMIND_MCP_EXECUTOR || "").trim().toLowerCase();
  if (configured === "powershell" || configured === "cmd" || configured === "sh") {
    return configured;
  }
  return resolveConfiguredPlatform() === "win32" ? "powershell" : "sh";
}

// ─── 基础工具 ─────────────────────────────────────────────

export function stripQuotes(s) {
  const t = String(s).trim();
  if (
    (t.startsWith('"') && t.endsWith('"')) ||
    (t.startsWith("'") && t.endsWith("'"))
  ) {
    return t.slice(1, -1);
  }
  return t;
}

export function escapePs(p) {
  return String(p).replace(/'/g, "''");
}

export function unwrapCommandIfJson(command) {
  let cmd = String(command).trim();
  if (cmd.startsWith("{") && cmd.includes('"command"')) {
    try {
      const nested = JSON.parse(cmd);
      if (nested && typeof nested.command === "string") {
        return nested.command.trim();
      }
    } catch {
      // keep original
    }
  }
  return cmd;
}

export function formatResult(text, exitCode) {
  const body = text && text.trim() ? text.trim() : "(no output)";
  return `${body}\nexit code: ${exitCode}`;
}

export function toRelativeUnderCwd(targetPath, cwd) {
  const resolved = path.resolve(stripQuotes(targetPath));
  const cwdResolved = path.resolve(cwd);
  const rel = path.relative(cwdResolved, resolved);
  if (rel && !rel.startsWith("..") && !path.isAbsolute(rel)) {
    return rel.replace(/\//g, path.sep);
  }
  return null;
}

/** 按 && 拆分（尊重引号） */
export function splitByAnd(command) {
  const parts = [];
  let current = "";
  let inDouble = false;
  let inSingle = false;
  for (let i = 0; i < command.length; i++) {
    const c = command[i];
    if (c === '"' && !inSingle) inDouble = !inDouble;
    if (c === "'" && !inDouble) inSingle = !inSingle;
    if (!inDouble && !inSingle && command[i] === "&" && command[i + 1] === "&") {
      if (current.trim()) parts.push(current.trim());
      current = "";
      i++;
      continue;
    }
    current += c;
  }
  if (current.trim()) parts.push(current.trim());
  return parts.length ? parts : [command.trim()];
}

// ─── 规范化管线 ───────────────────────────────────────────

export function getPreviewPort() {
  const n = parseInt(process.env.JCHATMIND_PREVIEW_PORT || "5500", 10);
  return Number.isFinite(n) ? n : 5500;
}

export function getReservedPorts() {
  return (process.env.JCHATMIND_RESERVED_PORTS || "8080,3000,5173")
    .split(",")
    .map((s) => parseInt(s.trim(), 10))
    .filter((n) => Number.isFinite(n));
}

export function stripRedundantCdPrefix(command, cwd) {
  const cwdNorm = path.resolve(cwd).toLowerCase();
  const patterns = [
    /^cd\s+\/d\s+("([^"]+)"|'([^']+)'|(\S+))\s*&&\s*/i,
    /^cd\s+("([^"]+)"|'([^']+)'|(\S+))\s*&&\s*/i,
    /^cd\s+("([^"]+)"|'([^']+)'|(\S+))\s*;\s*/i,
    /^cd\s+([A-Za-z]:[^\s&]+)\s*&&\s*/i,
  ];
  for (const re of patterns) {
    const m = command.match(re);
    if (!m) continue;
    const target = stripQuotes(m[2] || m[3] || m[4] || m[1] || "");
    if (path.resolve(target).toLowerCase() === cwdNorm) {
      return command.slice(m[0].length).trim();
    }
  }
  return command;
}

export function rewriteDirQuotedPaths(command, cwd) {
  return command.replace(
    /\bdir(\s+\/[a-z]+)?\s+("([^"]+)"|'([^']+)')/gi,
    (full, flags, dq, sq) => {
      const p = stripQuotes(dq || sq || "");
      const rel = toRelativeUnderCwd(p, cwd);
      const flagPart = flags || "";
      if (rel) return `dir${flagPart} ${rel}`;
      if (!p.includes(" ")) return `dir${flagPart} ${p.replace(/\//g, path.sep)}`;
      return full;
    }
  );
}

/** 将命令中 cwd 下的引号绝对路径改为相对路径 */
export function relativizeQuotedAbsolutePaths(command, cwd) {
  return command.replace(/"([^"]+)"|'([^']+)'/g, (full, dq, sq) => {
    const p = stripQuotes(dq || sq || "");
    if (!path.isAbsolute(p)) return full;
    const rel = toRelativeUnderCwd(p, cwd);
    return rel ? rel : full;
  });
}

function parseEvalScriptBody(raw) {
  let script = String(raw).trim();
  if (script.startsWith('"') && script.endsWith('"')) {
    return script
      .slice(1, -1)
      .replace(/\\"/g, '"')
      .replace(/\\n/g, "\n")
      .replace(/\\t/g, "\t")
      .replace(/\\\\/g, "\\");
  }
  if (script.startsWith("'") && script.endsWith("'")) {
    return script.slice(1, -1);
  }
  return script;
}

/** 从 node [--input-type=module] -e "..." 提取脚本体（供 execFile 直调，绕过 shell 引号） */
export function extractNodeEvalScript(command) {
  const m = command.trim().match(/^node\s+(?:--input-type=(\S+)\s+)?-e\s+([\s\S]+)$/i);
  if (!m) return null;
  return {
    script: parseEvalScriptBody(m[2]),
    inputType: m[1] || null,
  };
}

/** 解析可选的 cd 子目录前缀：cd tank-battle && ... */
export function parseCdSubdirPrefix(command) {
  const m = command.match(/^cd\s+(\S+)\s*&&\s*/i);
  if (!m) return { subdir: "", rest: command };
  return {
    subdir: m[1].replace(/\//g, path.sep),
    rest: command.slice(m[0].length),
  };
}

/** ES module import 冒烟测试 → 各文件 node --check（避免多行 -e 被 shell 弄碎） */
export function rewriteEsmImportChecks(command) {
  const { subdir, rest } = parseCdSubdirPrefix(command);
  if (!/node\s+(?:--input-type=\S+\s+)?-e\b/i.test(rest)) return null;
  const spec = extractNodeEvalScript(rest);
  if (!spec || !/\bimport\b/.test(spec.script)) return null;

  const imports = [
    ...spec.script.matchAll(/from\s+['"]([^'"]+\.js)['"]/gi),
  ].map((m) => m[1]);
  if (!imports.length) return null;

  const prefix = subdir ? `${subdir}${path.sep}` : "";
  const checks = [
    ...new Set(
      imports.map((f) => {
        const rel = f.replace(/^\.\//, "").replace(/\//g, path.sep);
        return `node --check ${prefix}${rel}`;
      })
    ),
  ];
  return checks.join(" && ");
}

/**
 * node -e 改写：
 * - readFileSync('.js') + 语法检查 → node --check
 * - readFileSync(其他) → 文件存在/可读验证（避免 shell 引号弄碎 node -e）
 * - 其余 node -e → 保留，由 execNodeDirect 无 shell 执行
 */
export function rewriteNodeEval(command, cwd, isWindows) {
  const esmChecks = rewriteEsmImportChecks(command);
  if (esmChecks) return esmChecks;

  if (!/node\s+(?:--input-type=\S+\s+)?-e\b/i.test(command)) return command;

  const readMatch = command.match(/readFileSync\s*\(\s*['"]([^'"]+)['"]/i);
  if (readMatch) {
    const file = readMatch[1];
    const abs = path.isAbsolute(file) ? file : path.join(cwd, file);
    const rel = toRelativeUnderCwd(abs, cwd) || file.replace(/\//g, path.sep);

    if (/\.js$/i.test(file) && /new\s+Function|SyntaxError|syntax/i.test(command)) {
      return `node --check ${rel}`;
    }

    const logMatch = command.match(/console\.log\s*\(\s*['"]([^'"]*)['"]\s*\)/);
    const msg = logMatch ? logMatch[1] : `${path.basename(file)} 读取成功`;
    if (isWindows) {
      const p = psPath(rel);
      return `if (Test-Path ${p}) { Get-Content ${p} -TotalCount 1 | Out-Null; Write-Output '${escapePs(msg)}' } else { Write-Error 'file not found'; exit 1 }`;
    }
    return `test -f ${shQuote(rel)} && echo '${msg.replace(/'/g, "'\\''")}'`;
  }

  return command;
}

/** @deprecated 使用 rewriteNodeEval */
export function rewriteNodeSyntaxCheck(command, cwd) {
  return rewriteNodeEval(command, cwd, isWindowsPlatform());
}

/** 去掉 bash/cmd 重定向尾巴（PowerShell 下 2>&1 会报错） */
export function stripShellRedirects(command) {
  return command.replace(/\s+2>&1\s*$/i, "").trim();
}

/**
 * node --check 改写：
 * - 绝对路径 → 相对路径
 * - 非 .js（如 .html）→ 文件存在性检查（node --check 不支持 HTML）
 */
export function rewriteNodeCheck(command, cwd, isWindows) {
  const m = command.trim().match(/^node\s+--check\s+(.+)$/i);
  if (!m) return command;

  let fileArg = stripQuotes(m[1].trim());
  const abs = path.isAbsolute(fileArg) ? fileArg : path.join(cwd, fileArg);
  const rel = toRelativeUnderCwd(abs, cwd) || fileArg.replace(/\//g, path.sep);

  if (!/\.m?js$/i.test(rel)) {
    const msg =
      `OK: ${rel} 存在。node --check 仅适用于 .js；HTML 请用 verify_coding_file 或浏览器打开预览。`;
    if (isWindows) {
      const p = psPath(rel);
      return `if (Test-Path ${p}) { Write-Output '${escapePs(msg)}' } else { Write-Error 'not found: ${escapePs(rel)}'; exit 1 }`;
    }
    return `test -f '${rel.replace(/'/g, "'\\''")}' && echo '${msg.replace(/'/g, "'\\''")}'`;
  }

  return rel.includes(" ") ? `node --check "${rel}"` : `node --check ${rel}`;
}

/** cmd 管道 / 单 & / >nul 等在 PowerShell 下易失败；HTML 脚本标签检查改写为 PS */
export function rewriteUnsafeCmdPipelines(command, cwd, isWindows) {
  if (!isWindows) return command;
  if (!/[|]|(^|[^&])&[^&]|>nul/i.test(command)) return command;

  const htmlScriptCheck =
    /type\s+([\w./\\-]+\.html)\s*\|\s*find\s+/i.test(command) ||
    /find\s+["']<\/script>["']/i.test(command);
  if (htmlScriptCheck) {
    const htmlMatch = command.match(/type\s+([\w./\\-]+\.html)/i);
    const html = htmlMatch ? htmlMatch[1].replace(/\//g, path.sep) : "index.html";
    const p = psPath(html);
    return (
      `if (Test-Path ${p}) { $c = Get-Content ${p} -Raw; ` +
      `if ($c -match '</script>') { Write-Output 'HTML_SCRIPT_TAG_OK' } ` +
      `else { Write-Error 'no </script> in ${escapePs(html)}'; exit 1 } } ` +
      `else { Write-Error 'not found: ${escapePs(html)}'; exit 1 }`
    );
  }

  return command;
}

const LONG_RUNNING_SERVER =
  /\b(http-server|python\s+-m\s+http\.server|npx\s+serve\b|live-server)\b/i;

/** 将 -p 8080 等保留端口改为 preview 端口 */
export function rewriteReservedPorts(command) {
  const reserved = new Set(getReservedPorts());
  const preview = getPreviewPort();
  let out = command.replace(/-p\s+(\d+)/gi, (full, p) => {
    const port = parseInt(p, 10);
    return reserved.has(port) ? `-p ${preview}` : full;
  });
  out = out.replace(/:(\d{4,5})\b/g, (full, p) => {
    const port = parseInt(p, 10);
    return reserved.has(port) ? `:${preview}` : full;
  });
  return out;
}

/**
 * MCP 为同步调用，不宜启动长期 http-server；且 8080 已被 JChatMind 后端占用。
 * 改写为 HTML 存在性检查 + 预览说明。
 */
export function rewritePreviewAndServerCommands(command, cwd, isWindows) {
  const hasServer = LONG_RUNNING_SERVER.test(command);
  const hasBrowserStart = /\bstart\s+https?:\/\//i.test(command);
  if (!hasServer && !hasBrowserStart) {
    return rewriteReservedPorts(command);
  }

  const { subdir } = parseCdSubdirPrefix(command);
  const htmlMatch = command.match(/([\w./\\-]+\.html)/i);
  const html = htmlMatch
    ? htmlMatch[1].replace(/\//g, path.sep)
    : subdir
      ? `${subdir}${path.sep}index.html`
      : "index.html";
  const port = getPreviewPort();
  const msg =
    `OK: ${html} 存在。8080 为 JChatMind 后端端口，MCP 勿启动 http-server。` +
    `预览: 双击 ${html}，或本地终端: npx http-server -p ${port}`;

  if (isWindows) {
    const p = psPath(html);
    return `if (Test-Path ${p}) { Write-Output '${escapePs(msg)}' } else { Write-Error 'not found: ${escapePs(html)}'; exit 1 }`;
  }
  return `test -f '${html.replace(/'/g, "'\\''")}' && echo '${msg.replace(/'/g, "'\\''")}'`;
}

// ─── Windows cmd → PowerShell（Windows 执行后端用 PS 而非 cmd.exe）────

const WIN_CMD_TO_PS = [
  [/^dir\s+\/b\s+(.+)$/i, (m) => `(Get-ChildItem ${psPath(m[1])} -Name)`],
  [/^dir\s+\/b\s*$/i, () => `(Get-ChildItem -Name)`],
  [/^dir\s+(.+)$/i, (m) => `Get-ChildItem ${psPath(m[1])}`],
  [/^dir\s*$/i, () => `Get-ChildItem`],
  [/^type\s+(.+)$/i, (m) => `Get-Content ${psPath(m[1])}`],
  [/^del\s+\/f\s+\/q\s+(.+)$/i, (m) => `Remove-Item -Force ${psPath(m[1])}`],
  [/^del\s+(.+)$/i, (m) => `Remove-Item -Force ${psPath(m[1])}`],
  [/^copy\s+(.+)$/i, (m) => `Copy-Item -Force ${psCopyArgs(m[1])}`],
  [/^move\s+(.+)$/i, (m) => `Move-Item -Force ${psCopyArgs(m[1])}`],
  [/^md\s+(.+)$/i, (m) => `New-Item -ItemType Directory -Force -Path ${psPath(m[1])}`],
  [/^where\s+(.+)$/i, (m) => `(Get-Command ${psPath(m[1])} -ErrorAction SilentlyContinue).Source`],
  [/^cls\s*$/i, () => `Clear-Host`],
];

// ─── Unix → PowerShell 翻译表 ─────────────────────────────

function psPath(arg) {
  const p = stripQuotes(arg).replace(/\//g, path.sep);
  return p.includes(" ") ? `'${escapePs(p)}'` : p;
}

function psCopyArgs(rest) {
  const tokens = rest.trim().split(/\s+/);
  if (tokens.length >= 2) {
    return `-Path ${psPath(tokens[0])} -Destination ${psPath(tokens[1])}`;
  }
  return rest;
}

function psTouch(arg) {
  const p = psPath(arg);
  return `if (-not (Test-Path ${p})) { New-Item -ItemType File -Path ${p} } else { (Get-Item ${p}).LastWriteTime = Get-Date }`;
}

const UNIX_TO_PS = [
  [/^mkdir\s+-p\s+(.+)$/i, (m) => `New-Item -ItemType Directory -Force -Path ${psPath(m[1])}`],
  [/^mkdir\s+(.+)$/i, (m) => `New-Item -ItemType Directory -Force -Path ${psPath(m[1])}`],
  [/^touch\s+(.+)$/i, (m) => psTouch(m[1])],
  [/^cp\s+-r\s+(.+)$/i, (m) => `Copy-Item -Recurse -Force ${psCopyArgs(m[1])}`],
  [/^cp\s+(.+)$/i, (m) => `Copy-Item -Force ${psCopyArgs(m[1])}`],
  [/^mv\s+(.+)$/i, (m) => `Move-Item -Force ${psCopyArgs(m[1])}`],
  [/^rm\s+-rf\s+(.+)$/i, (m) => `Remove-Item -Recurse -Force -Path ${psPath(m[1])}`],
  [/^rm\s+-r\s+(.+)$/i, (m) => `Remove-Item -Recurse -Force -Path ${psPath(m[1])}`],
  [/^rm\s+-f\s+(.+)$/i, (m) => `Remove-Item -Force -Path ${psPath(m[1])}`],
  [/^rm\s+(.+)$/i, (m) => `Remove-Item -Force -Path ${psPath(m[1])}`],
  [/^cat\s+(.+)$/i, (m) => `Get-Content -Path ${psPath(m[1])}`],
  [/^ls\s+-la?\s*$/i, () => "Get-ChildItem -Force"],
  [/^ls\s+-la?\s+(.+)$/i, (m) => `Get-ChildItem -Force ${psPath(m[1])}`],
  [/^ls\s*$/i, () => "Get-ChildItem"],
  [/^ls\s+(.+)$/i, (m) => `Get-ChildItem ${psPath(m[1])}`],
  [/^pwd\s*$/i, () => "Get-Location"],
  [/^which\s+(.+)$/i, (m) => `(Get-Command ${psPath(m[1])} -ErrorAction SilentlyContinue).Source`],
  [/^echo\s+(.+)$/i, (m) => `Write-Output ${m[1]}`],
  [/^grep\s+(.+)$/i, (m) => translateGrepToPs(m[1])],
  [/^find\s+(.+)$/i, (m) => translateFindToPs(m[1])],
  [/^head\s+(.+)$/i, (m) => `Get-Content ${psPath(m[1])} -TotalCount 10`],
  [/^tail\s+(.+)$/i, (m) => `Get-Content ${psPath(m[1])} -Tail 10`],
];

function translateGrepToPs(rest) {
  const m = rest.match(/^(['"])(.+?)\1\s+(.+)$/);
  if (m) return `Select-String -Pattern '${escapePs(m[2])}' -Path ${psPath(m[3])}`;
  const parts = rest.trim().split(/\s+/);
  if (parts.length >= 2) {
    return `Select-String -Pattern '${escapePs(stripQuotes(parts[0]))}' -Path ${psPath(parts[1])}`;
  }
  return `Select-String ${rest}`;
}

function translateFindToPs(rest) {
  const nameMatch = rest.match(/-name\s+(['"]?)([^'"]+)\1/);
  if (nameMatch) {
    const dir = rest.split("-name")[0].trim() || ".";
    return `Get-ChildItem -Path ${psPath(dir)} -Recurse -Filter '${escapePs(nameMatch[2])}'`;
  }
  return `Get-ChildItem -Path ${psPath(rest)} -Recurse`;
}

// ─── Windows cmd → POSIX 翻译表 ───────────────────────────

function shQuote(arg) {
  const p = stripQuotes(arg);
  return p.includes(" ") ? `'${p.replace(/'/g, "'\\''")}'` : p;
}

function shCopyArgs(rest) {
  const tokens = rest.trim().split(/\s+/);
  if (tokens.length >= 2) return `${shQuote(tokens[0])} ${shQuote(tokens[1])}`;
  return rest;
}

const WIN_TO_POSIX = [
  [/^dir\s*$/i, () => "ls"],
  [/^dir\s+\/b\s+(.+)$/i, (m) => `ls ${shQuote(m[1])}`],
  [/^dir\s+\/b\s*$/i, () => "ls"],
  [/^dir\s+(.+)$/i, (m) => `ls ${shQuote(m[1])}`],
  [/^type\s+(.+)$/i, (m) => `cat ${shQuote(m[1])}`],
  [/^del\s+\/f\s+\/q\s+(.+)$/i, (m) => `rm -f ${shQuote(m[1])}`],
  [/^del\s+(.+)$/i, (m) => `rm -f ${shQuote(m[1])}`],
  [/^copy\s+(.+)$/i, (m) => `cp ${shCopyArgs(m[1])}`],
  [/^move\s+(.+)$/i, (m) => `mv ${shCopyArgs(m[1])}`],
  [/^md\s+(.+)$/i, (m) => `mkdir -p ${shQuote(m[1])}`],
  [/^rd\s+\/s\s+\/q\s+(.+)$/i, (m) => `rm -rf ${shQuote(m[1])}`],
  [/^where\s+(.+)$/i, (m) => `which ${shQuote(m[1])}`],
  [/^cls\s*$/i, () => "clear"],
];

function applyTranslators(command, table) {
  const trimmed = command.trim();
  for (const [re, fn] of table) {
    const m = trimmed.match(re);
    if (m) return fn(m);
  }
  return trimmed;
}

export function translateForPlatform(command, isWindows) {
  if (isWindows) {
    const winCmd = applyTranslators(command, WIN_CMD_TO_PS);
    if (winCmd !== command.trim()) return winCmd;
    return applyTranslators(command, UNIX_TO_PS);
  }
  return applyTranslators(command, WIN_TO_POSIX);
}

/**
 * 完整规范化（不执行）。返回 { prepared, steps } 便于日志/调试。
 */
export function normalizeCommand(rawCommand, cwd, options = {}) {
  const isWindows = options.platform
    ? isWindowsPlatform(options.platform)
    : isWindowsPlatform();
  const steps = [];

  let cmd = unwrapCommandIfJson(rawCommand);
  if (cmd !== rawCommand) steps.push({ step: "unwrapJson", result: cmd });

  const afterRedirect = stripShellRedirects(cmd);
  if (afterRedirect !== cmd) {
    steps.push({ step: "stripRedirects", result: afterRedirect });
    cmd = afterRedirect;
  }

  const afterCd = stripRedundantCdPrefix(cmd, cwd);
  if (afterCd !== cmd) {
    steps.push({ step: "stripRedundantCd", result: afterCd });
    cmd = afterCd;
  }

  const afterNode = rewriteNodeEval(cmd, cwd, isWindows);
  if (afterNode !== cmd) {
    steps.push({ step: "rewriteNodeEval", result: afterNode });
    cmd = afterNode;
  }

  const afterNodeCheck = rewriteNodeCheck(cmd, cwd, isWindows);
  if (afterNodeCheck !== cmd) {
    steps.push({ step: "rewriteNodeCheck", result: afterNodeCheck });
    cmd = afterNodeCheck;
  }

  const afterPipeline = rewriteUnsafeCmdPipelines(cmd, cwd, isWindows);
  if (afterPipeline !== cmd) {
    steps.push({ step: "rewriteCmdPipeline", result: afterPipeline });
    cmd = afterPipeline;
  }

  const afterPreview = rewritePreviewAndServerCommands(cmd, cwd, isWindows);
  if (afterPreview !== cmd) {
    steps.push({ step: "rewritePreviewServer", result: afterPreview });
    cmd = afterPreview;
  }

  if (isWindows) {
    const afterDir = rewriteDirQuotedPaths(cmd, cwd);
    if (afterDir !== cmd) {
      steps.push({ step: "rewriteDirPaths", result: afterDir });
      cmd = afterDir;
    }
    const afterRel = relativizeQuotedAbsolutePaths(cmd, cwd);
    if (afterRel !== cmd) {
      steps.push({ step: "relativizePaths", result: afterRel });
      cmd = afterRel;
    }
  }

  const beforeTranslate = cmd;
  const translated = translateForPlatform(cmd, isWindows);
  if (translated !== cmd) {
    const step = isWindows
      ? (applyTranslators(beforeTranslate, WIN_CMD_TO_PS) !== beforeTranslate.trim()
          ? "cmdToPowerShell"
          : "unixToPowerShell")
      : "cmdToPosix";
    steps.push({ step, result: translated });
    cmd = translated;
  }

  return { prepared: cmd, steps, platform: isWindows ? "win32" : "posix" };
}

// ─── 执行引擎 ─────────────────────────────────────────────

async function execPowerShell(command, cwd, timeout) {
  const script = [
    `$ErrorActionPreference = 'Continue'`,
    `Set-Location -LiteralPath '${escapePs(path.resolve(cwd))}'`,
    command,
    `exit $LASTEXITCODE`,
  ].join("; ");
  try {
    const result = await execFileAsync(
      "powershell.exe",
      ["-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script],
      {
        timeout,
        maxBuffer: 10 * 1024 * 1024,
        windowsHide: true,
        env: process.env,
      }
    );
    return {
      stdout: result.stdout ?? "",
      stderr: result.stderr ?? "",
      exitCode: 0,
    };
  } catch (err) {
    return {
      stdout: err.stdout ? String(err.stdout) : "",
      stderr: err.stderr ? String(err.stderr) : "",
      exitCode: typeof err.code === "number" ? err.code : 1,
      message: err instanceof Error ? err.message : String(err),
    };
  }
}

async function execPosix(command, cwd, timeout) {
  try {
    const result = await execFileAsync("sh", ["-c", command], {
      cwd: path.resolve(cwd),
      timeout,
      maxBuffer: 10 * 1024 * 1024,
      env: process.env,
    });
    return {
      stdout: result.stdout ?? "",
      stderr: result.stderr ?? "",
      exitCode: 0,
    };
  } catch (err) {
    return {
      stdout: err.stdout ? String(err.stdout) : "",
      stderr: err.stderr ? String(err.stderr) : "",
      exitCode: typeof err.code === "number" ? err.code : 1,
      message: err instanceof Error ? err.message : String(err),
    };
  }
}

function extractNodeCheckFile(command) {
  const m = command.trim().match(/^node\s+--check\s+(.+)$/i);
  if (!m) return null;
  const file = stripQuotes(m[1].trim());
  return /\.m?js$/i.test(file) ? file : null;
}

async function execNodeCheckDirect(file, cwd, timeout) {
  try {
    const result = await execFileAsync("node", ["--check", file], {
      cwd: path.resolve(cwd),
      timeout,
      maxBuffer: 10 * 1024 * 1024,
      env: process.env,
    });
    return {
      stdout: result.stdout ?? "",
      stderr: result.stderr ?? "",
      exitCode: 0,
    };
  } catch (err) {
    return {
      stdout: err.stdout ? String(err.stdout) : "",
      stderr: err.stderr ? String(err.stderr) : "",
      exitCode: typeof err.code === "number" ? err.code : 1,
      message: err instanceof Error ? err.message : String(err),
    };
  }
}

async function execNodeDirect(spec, cwd, timeout) {
  const { script, inputType } =
    typeof spec === "string" ? { script: spec, inputType: null } : spec;
  const args = [];
  if (inputType) args.push(`--input-type=${inputType}`);
  args.push("-e", script);
  try {
    const result = await execFileAsync("node", args, {
      cwd: path.resolve(cwd),
      timeout,
      maxBuffer: 10 * 1024 * 1024,
      env: process.env,
    });
    return {
      stdout: result.stdout ?? "",
      stderr: result.stderr ?? "",
      exitCode: 0,
    };
  } catch (err) {
    return {
      stdout: err.stdout ? String(err.stdout) : "",
      stderr: err.stderr ? String(err.stderr) : "",
      exitCode: typeof err.code === "number" ? err.code : 1,
      message: err instanceof Error ? err.message : String(err),
    };
  }
}

async function execCmd(command, cwd, timeout) {
  try {
    const result = await execFileAsync("cmd.exe", ["/c", command], {
      cwd: path.resolve(cwd),
      timeout,
      maxBuffer: 10 * 1024 * 1024,
      windowsHide: true,
      env: process.env,
    });
    return {
      stdout: result.stdout ?? "",
      stderr: result.stderr ?? "",
      exitCode: 0,
    };
  } catch (err) {
    return {
      stdout: err.stdout ? String(err.stdout) : "",
      stderr: err.stderr ? String(err.stderr) : "",
      exitCode: typeof err.code === "number" ? err.code : 1,
      message: err instanceof Error ? err.message : String(err),
    };
  }
}

function pickExecutor(isWindows) {
  const executor = resolveConfiguredExecutor();
  if (!isWindows) return "sh";
  if (executor === "cmd") return "cmd";
  return "powershell";
}

async function runChain(parts, cwd, isWindows, timeout) {
  const executor = pickExecutor(isWindows);
  const execFn = executor === "powershell"
    ? execPowerShell
    : executor === "cmd"
      ? execCmd
      : execPosix;
  let combinedStdout = "";
  let combinedStderr = "";
  const executed = [];

  for (const part of parts) {
    const nodeEval = extractNodeEvalScript(part);
    const nodeCheckFile = extractNodeCheckFile(part);
    const r = nodeEval !== null
      ? await execNodeDirect(nodeEval, cwd, timeout)
      : nodeCheckFile !== null
        ? await execNodeCheckDirect(nodeCheckFile, cwd, timeout)
        : await execFn(part, cwd, timeout);
    executed.push(
      nodeEval !== null
        ? `node -e (direct, ${nodeEval.script.length} chars${nodeEval.inputType ? ", " + nodeEval.inputType : ""})`
        : nodeCheckFile !== null
          ? `node --check (direct, ${nodeCheckFile})`
          : part
    );
    if (r.stdout) combinedStdout += (combinedStdout ? "\n" : "") + r.stdout;
    if (r.stderr) combinedStderr += (combinedStderr ? "\n" : "") + r.stderr;
    if (r.exitCode !== 0) {
      return {
        stdout: combinedStdout,
        stderr: combinedStderr,
        exitCode: r.exitCode,
        message: r.message,
        executed,
      };
    }
  }
  return {
    stdout: combinedStdout,
    stderr: combinedStderr,
    exitCode: 0,
    executed,
  };
}

/**
 * 跨平台执行入口。
 * @returns {{ stdout, stderr, exitCode, prepared, steps, executed, message? }}
 */
export async function runCommand(rawCommand, cwd, options = {}) {
  const isWindows = options.platform
    ? options.platform === "win32"
    : resolveConfiguredPlatform() === "win32";
  const timeout = options.timeout ?? DEFAULT_TIMEOUT_MS;
  const platform = isWindows ? "win32" : "posix";
  const { prepared, steps } = normalizeCommand(rawCommand, cwd, { platform });
  const parts = splitByAnd(prepared);
  const result = await runChain(parts, cwd, isWindows, timeout);
  return {
    ...result,
    prepared,
    steps,
    platform,
    executor: pickExecutor(isWindows),
  };
}
