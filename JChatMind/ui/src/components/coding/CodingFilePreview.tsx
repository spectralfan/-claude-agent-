import React, { useEffect, useMemo, useState } from "react";
import { Empty, Spin, Tabs, Tag, Typography } from "antd";
import Editor from "@monaco-editor/react";
import { getCodingTaskFile } from "../../api/api.ts";
import {
  computeLineDiff,
  monacoLanguageFromHint,
} from "./codingEditorUtils.ts";

const { Text } = Typography;

export interface FileDiffState {
  relativePath: string;
  changeType?: string;
  oldContent?: string;
  newContent?: string;
}

interface CodingFilePreviewProps {
  taskId: string;
  filePath?: string;
  diff?: FileDiffState | null;
  stackLanguage?: string;
}

const DiffLineView: React.FC<{ lines: ReturnType<typeof computeLineDiff> }> = ({
  lines,
}) => (
  <pre className="flex-1 overflow-auto m-0 p-3 text-xs font-mono leading-relaxed">
    {lines.map((line, idx) => (
      <div
        key={idx}
        className={
          line.type === "add"
            ? "bg-emerald-900/40 text-emerald-100"
            : line.type === "remove"
              ? "bg-red-900/30 text-red-200 line-through opacity-80"
              : "text-gray-300"
        }
      >
        {line.type === "add" ? "+ " : line.type === "remove" ? "- " : "  "}
        {line.text || " "}
      </div>
    ))}
  </pre>
);

const CodingFilePreview: React.FC<CodingFilePreviewProps> = ({
  taskId,
  filePath,
  diff,
  stackLanguage,
}) => {
  const [content, setContent] = useState<string>("");
  const [language, setLanguage] = useState<string>("text");
  const [truncated, setTruncated] = useState(false);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState<string>("preview");
  const [diffViewMode, setDiffViewMode] = useState<"inline" | "split">("inline");

  useEffect(() => {
    if (diff) {
      setActiveTab("diff");
    }
  }, [diff?.relativePath, diff?.newContent]);

  useEffect(() => {
    if (!filePath || !taskId) {
      setContent("");
      return;
    }
    setLoading(true);
    getCodingTaskFile(taskId, filePath)
      .then((file) => {
        setContent(file.content);
        setLanguage(file.language);
        setTruncated(file.truncated);
      })
      .catch(() => {
        setContent("");
      })
      .finally(() => setLoading(false));
  }, [taskId, filePath]);

  const monacoLang = useMemo(
    () => monacoLanguageFromHint(stackLanguage ?? language, filePath),
    [stackLanguage, language, filePath],
  );

  const inlineDiffLines = useMemo(
    () => computeLineDiff(diff?.oldContent, diff?.newContent ?? content),
    [diff?.oldContent, diff?.newContent, content],
  );

  if (!filePath) {
    return (
      <div className="h-full flex items-center justify-center bg-white">
        <Empty description="从左侧选择文件，或在对话中让 Agent 修改代码" />
      </div>
    );
  }

  const previewPane = loading ? (
    <div className="flex justify-center py-12">
      <Spin />
    </div>
  ) : (
    <div className="h-full flex flex-col min-h-0">
      <div className="px-4 py-2 border-b border-gray-100 flex items-center gap-2 shrink-0">
        <Text code className="text-xs">
          {filePath}
        </Text>
        <Tag>{monacoLang}</Tag>
        {truncated && <Tag color="orange">内容已截断</Tag>}
      </div>
      <div className="flex-1 min-h-0">
        <Editor
          height="100%"
          language={monacoLang}
          value={content || ""}
          theme="vs-dark"
          options={{
            readOnly: true,
            minimap: { enabled: false },
            fontSize: 13,
            scrollBeyondLastLine: false,
            wordWrap: "on",
          }}
        />
      </div>
    </div>
  );

  const diffPane =
    diff && (diff.oldContent != null || diff.newContent != null) ? (
      <div className="h-full flex flex-col min-h-0">
        <div className="px-4 py-2 border-b border-gray-100 shrink-0 flex items-center justify-between">
          <Text type="secondary" className="text-xs">
            {diff.changeType === "created" ? "新建文件" : "文件已修改"} ·{" "}
            {diff.relativePath}
          </Text>
          <Tag>行级 Diff</Tag>
        </div>
        <Tabs
          size="small"
          className="px-2 shrink-0"
          activeKey={diffViewMode}
          onChange={(k) => setDiffViewMode(k as "inline" | "split")}
          items={[
            { key: "inline", label: "行级" },
            { key: "split", label: "并排" },
          ]}
        />
        {diffViewMode === "inline" ? (
          <div className="flex-1 min-h-0 flex flex-col bg-[#1e1e1e]">
            <DiffLineView lines={inlineDiffLines} />
          </div>
        ) : (
          <div className="flex-1 grid grid-cols-2 min-h-0">
            <div className="flex flex-col border-r border-gray-700 min-h-0">
              <div className="px-3 py-1 bg-gray-800 text-xs text-gray-400 shrink-0">
                修改前
              </div>
              <Editor
                height="100%"
                language={monacoLang}
                value={diff.oldContent ?? ""}
                theme="vs-dark"
                options={{
                  readOnly: true,
                  minimap: { enabled: false },
                  fontSize: 12,
                }}
              />
            </div>
            <div className="flex flex-col min-h-0">
              <div className="px-3 py-1 bg-emerald-900/30 text-xs text-emerald-300 shrink-0">
                修改后
              </div>
              <Editor
                height="100%"
                language={monacoLang}
                value={diff.newContent ?? content}
                theme="vs-dark"
                options={{
                  readOnly: true,
                  minimap: { enabled: false },
                  fontSize: 12,
                }}
              />
            </div>
          </div>
        )}
      </div>
    ) : (
      <Empty description="暂无 Diff，Agent 写入文件后会在此展示" />
    );

  return (
    <div className="h-full flex flex-col bg-white border-r border-gray-200 [&_.ant-tabs]:flex [&_.ant-tabs]:flex-col [&_.ant-tabs]:h-full [&_.ant-tabs-content]:flex-1 [&_.ant-tabs-content]:min-h-0 [&_.ant-tabs-tabpane]:h-full">
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        className="h-full coding-preview-tabs"
        items={[
          { key: "preview", label: "预览", children: previewPane },
          { key: "diff", label: "Diff", children: diffPane },
        ]}
        tabBarStyle={{ marginBottom: 0, paddingLeft: 12, paddingRight: 12 }}
      />
    </div>
  );
};

export default CodingFilePreview;
