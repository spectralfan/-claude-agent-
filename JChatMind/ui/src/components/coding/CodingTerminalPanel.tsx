import React from "react";
import { Button, Empty, Tag } from "antd";
import {
  CheckOutlined,
  CloseOutlined,
  DownOutlined,
  UpOutlined,
} from "@ant-design/icons";

export interface TerminalLogEntry {
  time: string;
  type: string;
  text: string;
}

interface CodingTerminalPanelProps {
  logs: TerminalLogEntry[];
  collapsed: boolean;
  onToggleCollapse: () => void;
  approvalCommand?: string;
  onApprove?: () => void;
  onReject?: () => void;
  approving?: boolean;
}

const CodingTerminalPanel: React.FC<CodingTerminalPanelProps> = ({
  logs,
  collapsed,
  onToggleCollapse,
  approvalCommand,
  onApprove,
  onReject,
  approving,
}) => {
  return (
    <div className="border-t border-gray-200 bg-gray-900 text-gray-100 shrink-0">
      {approvalCommand && (
        <div className="flex items-center justify-between gap-3 px-4 py-2 bg-amber-900/40 border-b border-amber-700/50">
          <div className="min-w-0 flex-1">
            <Tag color="gold">待审批</Tag>
            <code className="text-xs text-amber-100 break-all">
              {approvalCommand}
            </code>
          </div>
          <div className="shrink-0 flex gap-2">
            <Button
              size="small"
              danger
              icon={<CloseOutlined />}
              loading={approving}
              onClick={onReject}
            >
              拒绝
            </Button>
            <Button
              size="small"
              type="primary"
              icon={<CheckOutlined />}
              loading={approving}
              onClick={onApprove}
            >
              批准
            </Button>
          </div>
        </div>
      )}
      <div className="flex items-center justify-between px-3 py-1 bg-gray-800">
        <span className="text-xs text-gray-400">终端 / 执行日志</span>
        <Button
          type="text"
          size="small"
          className="text-gray-400"
          icon={collapsed ? <UpOutlined /> : <DownOutlined />}
          onClick={onToggleCollapse}
        >
          {collapsed ? "展开" : "收起"}
        </Button>
      </div>
      {!collapsed && (
        <div className="max-h-48 overflow-y-auto p-3 text-xs font-mono">
          {logs.length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <span className="text-gray-500">Maven 输出与 Coding 事件将显示在此</span>
              }
            />
          ) : (
            logs.map((l, i) => (
              <div key={i} className="whitespace-pre-wrap mb-1">
                <span className="text-gray-500">[{l.time}]</span>{" "}
                <span className="text-cyan-400">{l.type}</span> {l.text}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
};

export default CodingTerminalPanel;
