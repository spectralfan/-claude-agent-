/** 简单行级 Diff 行数据 */
export interface DiffLine {
  type: "same" | "add" | "remove";
  text: string;
}

/** 基于 LCS 的行级 diff（轻量，无外部依赖） */
export function computeLineDiff(
  oldText: string | undefined,
  newText: string | undefined,
): DiffLine[] {
  const oldLines = (oldText ?? "").split("\n");
  const newLines = (newText ?? "").split("\n");
  const m = oldLines.length;
  const n = newLines.length;
  const dp: number[][] = Array.from({ length: m + 1 }, () =>
    Array(n + 1).fill(0),
  );
  for (let i = m - 1; i >= 0; i--) {
    for (let j = n - 1; j >= 0; j--) {
      dp[i][j] =
        oldLines[i] === newLines[j]
          ? dp[i + 1][j + 1] + 1
          : Math.max(dp[i + 1][j], dp[i][j + 1]);
    }
  }
  const result: DiffLine[] = [];
  let i = 0;
  let j = 0;
  while (i < m && j < n) {
    if (oldLines[i] === newLines[j]) {
      result.push({ type: "same", text: oldLines[i] });
      i++;
      j++;
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      result.push({ type: "remove", text: oldLines[i] });
      i++;
    } else {
      result.push({ type: "add", text: newLines[j] });
      j++;
    }
  }
  while (i < m) {
    result.push({ type: "remove", text: oldLines[i++] });
  }
  while (j < n) {
    result.push({ type: "add", text: newLines[j++] });
  }
  return result;
}

export function monacoLanguageFromHint(
  language: string,
  filePath?: string,
): string {
  const ext = filePath?.split(".").pop()?.toLowerCase();
  const extMap: Record<string, string> = {
    java: "java",
    py: "python",
    js: "javascript",
    ts: "typescript",
    tsx: "typescript",
    jsx: "javascript",
    json: "json",
    xml: "xml",
    md: "markdown",
    yml: "yaml",
    yaml: "yaml",
    html: "html",
    css: "css",
    go: "go",
    rs: "rust",
  };
  if (ext && extMap[ext]) return extMap[ext];
  if (language === "java") return "java";
  if (language === "python") return "python";
  if (language === "javascript") return "javascript";
  return "plaintext";
}
