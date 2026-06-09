import React, { useCallback, useEffect, useMemo, useState } from "react";
import { Button, Empty, List, Tag, Typography } from "antd";
import { DownOutlined, UpOutlined } from "@ant-design/icons";
import { getCodingSubtasks, type CodingSubtaskVO } from "../../api/api.ts";

const { Text } = Typography;

const STATUS_COLOR: Record<string, string> = {
  PENDING: "default",
  READY: "blue",
  RUNNING: "processing",
  COMPLETED: "success",
  FAILED: "error",
  CANCELLED: "default",
};

const ROLE_COLOR: Record<string, string> = {
  WORKER: "geekblue",
  REVIEWER: "purple",
};

interface CodingSubtaskPanelProps {
  sessionId: string;
  refreshToken?: number;
  defaultCollapsed?: boolean;
}

const CodingSubtaskPanel: React.FC<CodingSubtaskPanelProps> = ({
  sessionId,
  refreshToken = 0,
  defaultCollapsed = true,
}) => {
  const [subtasks, setSubtasks] = useState<CodingSubtaskVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [collapsed, setCollapsed] = useState(defaultCollapsed);

  const load = useCallback(async () => {
    if (!sessionId) return;
    setLoading(true);
    try {
      setSubtasks(await getCodingSubtasks(sessionId));
    } catch {
      setSubtasks([]);
    } finally {
      setLoading(false);
    }
  }, [sessionId]);

  useEffect(() => {
    load();
  }, [load, refreshToken]);

  const runningCount = useMemo(
    () => subtasks.filter((t) => t.status === "RUNNING").length,
    [subtasks],
  );

  if (!sessionId) {
    return null;
  }

  const title = `编排任务 (${subtasks.length}${
    runningCount > 0 ? ` · 并行 ${runningCount}` : ""
  })`;

  return (
    <div className="border-b border-gray-100 shrink-0">
      <div className="px-3 py-1.5 flex items-center justify-between gap-2">
        <Text strong className="text-xs truncate">
          {title}
        </Text>
        <Button
          type="text"
          size="small"
          className="text-xs shrink-0"
          icon={collapsed ? <DownOutlined /> : <UpOutlined />}
          onClick={() => setCollapsed((v) => !v)}
        >
          {collapsed ? "展开" : "收起"}
        </Button>
      </div>
      {!collapsed && (
        <div className="px-3 pb-2 max-h-36 overflow-y-auto">
          {subtasks.length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="暂无编排任务"
              className="my-1"
            />
          ) : (
            <List
              size="small"
              loading={loading}
              dataSource={subtasks}
              renderItem={(item) => (
                <List.Item className="py-1 px-0">
                  <div className="flex flex-col gap-0.5 w-full min-w-0">
                    <div className="flex items-center gap-2 min-w-0 flex-wrap">
                      <Tag
                        color={STATUS_COLOR[item.status] ?? "default"}
                        className="text-xs shrink-0"
                      >
                        {item.status}
                      </Tag>
                      {item.role && (
                        <Tag
                          color={ROLE_COLOR[item.role] ?? "default"}
                          className="text-xs shrink-0"
                        >
                          {item.role}
                        </Tag>
                      )}
                      <Text className="text-xs truncate">{item.title}</Text>
                    </div>
                    {item.dependsOn && item.dependsOn.length > 0 && (
                      <Text type="secondary" className="text-xs truncate">
                        依赖: {item.dependsOn.join(", ")}
                      </Text>
                    )}
                    {item.resultSummary && (
                      <Text type="secondary" className="text-xs truncate">
                        {item.resultSummary}
                      </Text>
                    )}
                    {item.errorMessage && (
                      <Text type="danger" className="text-xs truncate">
                        {item.errorMessage}
                      </Text>
                    )}
                  </div>
                </List.Item>
              )}
            />
          )}
        </div>
      )}
    </div>
  );
};

export default CodingSubtaskPanel;
