import React, {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { Button, Tabs } from "antd";
import { MenuFoldOutlined, MenuUnfoldOutlined } from "@ant-design/icons";

const STORAGE_KEY = "jchatmind.coding.layout.v1";
const HANDLE_WIDTH = 4;
const TREE_COLLAPSED_STRIP = 28;

const DEFAULT_TREE_WIDTH = 200;
const DEFAULT_CODE_RATIO = 0.3;
const MIN_TREE = 0;
const MAX_TREE = 280;
const MIN_CODE = 240;
const MAX_CODE_RATIO = 0.55;
const MIN_CHAT = 360;
const MAX_CHAT_RATIO = 0.6;

type NarrowMode = "normal" | "compact" | "tabs";

interface StoredLayout {
  treeWidth: number;
  treeCollapsed: boolean;
  codeRatio: number;
}

interface CodingWorkspaceLayoutProps {
  fileTree: ReactNode;
  codePreview: ReactNode;
  chat: ReactNode;
  onCodeWidthChange?: (width: number) => void;
}

function loadLayout(): StoredLayout {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return {
        treeWidth: DEFAULT_TREE_WIDTH,
        treeCollapsed: false,
        codeRatio: DEFAULT_CODE_RATIO,
      };
    }
    const parsed = JSON.parse(raw) as Partial<StoredLayout>;
    return {
      treeWidth:
        typeof parsed.treeWidth === "number"
          ? Math.min(MAX_TREE, Math.max(MIN_TREE, parsed.treeWidth))
          : DEFAULT_TREE_WIDTH,
      treeCollapsed: Boolean(parsed.treeCollapsed),
      codeRatio:
        typeof parsed.codeRatio === "number"
          ? Math.min(MAX_CODE_RATIO, Math.max(0.15, parsed.codeRatio))
          : DEFAULT_CODE_RATIO,
    };
  } catch {
    return {
      treeWidth: DEFAULT_TREE_WIDTH,
      treeCollapsed: false,
      codeRatio: DEFAULT_CODE_RATIO,
    };
  }
}

function saveLayout(state: StoredLayout) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // ignore quota errors
  }
}

const ResizeHandle: React.FC<{
  onMouseDown: (e: React.MouseEvent) => void;
  onDoubleClick: () => void;
}> = ({ onMouseDown, onDoubleClick }) => (
  <div
    role="separator"
    aria-orientation="vertical"
    className="shrink-0 w-1 cursor-col-resize bg-gray-200 hover:bg-emerald-400 active:bg-emerald-500 transition-colors select-none"
    onMouseDown={onMouseDown}
    onDoubleClick={onDoubleClick}
    title="拖拽调整宽度，双击恢复默认"
  />
);

const CodingWorkspaceLayout: React.FC<CodingWorkspaceLayoutProps> = ({
  fileTree,
  codePreview,
  chat,
  onCodeWidthChange,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [stored, setStored] = useState<StoredLayout>(loadLayout);
  const [containerWidth, setContainerWidth] = useState(1200);
  const [narrowMode, setNarrowMode] = useState<NarrowMode>("normal");
  const [activeTab, setActiveTab] = useState("chat");
  const dragRef = useRef<{
    kind: "tree-code" | "code-chat";
    startX: number;
    startTree: number;
    startCode: number;
  } | null>(null);

  const treeWidth = stored.treeCollapsed ? 0 : stored.treeWidth;
  const treeStrip = stored.treeCollapsed ? TREE_COLLAPSED_STRIP : 0;
  const handlesWidth = stored.treeCollapsed ? HANDLE_WIDTH : HANDLE_WIDTH * 2;
  const available =
    containerWidth - treeWidth - treeStrip - handlesWidth;

  const computeWidths = useCallback(() => {
    let codeW = Math.round(available * stored.codeRatio);
    codeW = Math.min(
      Math.round(containerWidth * MAX_CODE_RATIO),
      Math.max(MIN_CODE, codeW),
    );
    let chatW = available - codeW;
    chatW = Math.min(
      Math.round(containerWidth * MAX_CHAT_RATIO),
      Math.max(MIN_CHAT, chatW),
    );
    if (codeW + chatW > available) {
      codeW = Math.max(MIN_CODE, available - chatW);
    }
    return { codeW, chatW };
  }, [available, containerWidth, stored.codeRatio]);

  const { codeW, chatW } = computeWidths();

  useEffect(() => {
    onCodeWidthChange?.(codeW);
  }, [codeW, onCodeWidthChange]);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const ro = new ResizeObserver((entries) => {
      const w = entries[0]?.contentRect.width ?? 1200;
      setContainerWidth(w);
      if (w < 720) {
        setNarrowMode("tabs");
      } else if (w < 900) {
        setNarrowMode("compact");
      } else {
        setNarrowMode("normal");
      }
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  useEffect(() => {
    if (narrowMode === "compact" && !stored.treeCollapsed) {
      setStored((prev) => {
        const next = { ...prev, treeCollapsed: true };
        saveLayout(next);
        return next;
      });
    }
  }, [narrowMode, stored.treeCollapsed]);

  const persist = useCallback((next: StoredLayout) => {
    setStored(next);
    saveLayout(next);
  }, []);

  const resetDefaults = useCallback(() => {
    persist({
      treeWidth: DEFAULT_TREE_WIDTH,
      treeCollapsed: false,
      codeRatio: DEFAULT_CODE_RATIO,
    });
  }, [persist]);

  const toggleTree = useCallback(() => {
    setStored((prev) => {
      const next = { ...prev, treeCollapsed: !prev.treeCollapsed };
      saveLayout(next);
      return next;
    });
  }, []);

  const startDrag =
    (kind: "tree-code" | "code-chat") => (e: React.MouseEvent) => {
      e.preventDefault();
      dragRef.current = {
        kind,
        startX: e.clientX,
        startTree: stored.treeWidth,
        startCode: codeW,
      };

      const onMove = (ev: MouseEvent) => {
        const drag = dragRef.current;
        if (!drag || !containerRef.current) return;
        const dx = ev.clientX - drag.startX;

        if (drag.kind === "tree-code" && !stored.treeCollapsed) {
          const nextTree = Math.min(
            MAX_TREE,
            Math.max(MIN_TREE, drag.startTree + dx),
          );
          setStored((prev) => {
            const next = { ...prev, treeWidth: nextTree };
            saveLayout(next);
            return next;
          });
        }

        if (drag.kind === "code-chat") {
          const avail =
            containerRef.current.clientWidth -
            (stored.treeCollapsed ? TREE_COLLAPSED_STRIP : stored.treeWidth) -
            handlesWidth;
          const nextCode = Math.min(
            Math.round(containerWidth * MAX_CODE_RATIO),
            Math.max(MIN_CODE, drag.startCode + dx),
          );
          const nextChat = avail - nextCode;
          if (nextChat >= MIN_CHAT && nextChat <= avail) {
            const ratio = nextCode / Math.max(1, avail);
            setStored((prev) => {
              const next = { ...prev, codeRatio: ratio };
              saveLayout(next);
              return next;
            });
          }
        }
      };

      const onUp = () => {
        dragRef.current = null;
        document.removeEventListener("mousemove", onMove);
        document.removeEventListener("mouseup", onUp);
        document.body.style.cursor = "";
        document.body.style.userSelect = "";
      };

      document.body.style.cursor = "col-resize";
      document.body.style.userSelect = "none";
      document.addEventListener("mousemove", onMove);
      document.addEventListener("mouseup", onUp);
    };

  if (narrowMode === "tabs") {
    return (
      <div ref={containerRef} className="flex-1 flex flex-col min-h-0 min-w-0">
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          size="small"
          className="shrink-0 px-2"
          items={[
            { key: "chat", label: "对话" },
            { key: "code", label: "代码" },
            { key: "files", label: "文件" },
          ]}
        />
        <div className="flex-1 min-h-0 min-w-0 overflow-hidden">
          {activeTab === "chat" && (
            <div className="h-full flex flex-col min-h-0 border-l-0">{chat}</div>
          )}
          {activeTab === "code" && (
            <div className="h-full min-h-0 overflow-hidden">{codePreview}</div>
          )}
          {activeTab === "files" && (
            <div className="h-full min-h-0 overflow-hidden">{fileTree}</div>
          )}
        </div>
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      className="flex-1 flex min-h-0 min-w-0 overflow-hidden"
    >
      {stored.treeCollapsed ? (
        <div
          className="shrink-0 flex flex-col items-center border-r border-gray-200 bg-gray-50"
          style={{ width: TREE_COLLAPSED_STRIP }}
        >
          <Button
            type="text"
            size="small"
            icon={<MenuUnfoldOutlined />}
            onClick={toggleTree}
            title="展开文件树"
            className="mt-2"
          />
        </div>
      ) : (
        <>
          <div
            className="shrink-0 min-h-0 overflow-hidden flex flex-col"
            style={{ width: treeWidth }}
          >
            <div className="shrink-0 flex items-center justify-between px-2 py-1 border-b border-gray-200 bg-gray-50">
              <span className="text-xs text-gray-500">文件树</span>
              <Button
                type="text"
                size="small"
                icon={<MenuFoldOutlined />}
                onClick={toggleTree}
                title="收起文件树"
              />
            </div>
            <div className="flex-1 min-h-0 overflow-hidden">{fileTree}</div>
          </div>
          <ResizeHandle
            onMouseDown={startDrag("tree-code")}
            onDoubleClick={resetDefaults}
          />
        </>
      )}

      <div
        className="shrink-0 min-h-0 min-w-0 overflow-hidden"
        style={{ width: codeW }}
      >
        {codePreview}
      </div>

      <ResizeHandle
        onMouseDown={startDrag("code-chat")}
        onDoubleClick={resetDefaults}
      />

      <div
        className="shrink-0 min-h-0 min-w-0 flex flex-col border-l border-gray-200 overflow-hidden"
        style={{ width: chatW, minWidth: MIN_CHAT }}
      >
        {chat}
      </div>
    </div>
  );
};

export default CodingWorkspaceLayout;
