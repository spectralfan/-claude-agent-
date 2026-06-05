import React, { useCallback, useEffect, useState } from "react";
import { Empty, List, Tag, Typography } from "antd";
import { getCodingSubtasks, type CodingSubtaskVO } from "../../api/api.ts";

const { Text } = Typography;

const STATUS_COLOR: Record<string, string> = {
  PENDING: "default",
  RUNNING: "processing",
  COMPLETED: "success",
  FAILED: "error",
};

interface CodingSubtaskPanelProps {
  sessionId: string;
  refreshToken?: number;
}

const CodingSubtaskPanel: React.FC<CodingSubtaskPanelProps> = ({
  sessionId,
  refreshToken = 0,
}) => {
  const [subtasks, setSubtasks] = useState<CodingSubtaskVO[]>([]);
  const [loading, setLoading] = useState(false);

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

  if (!sessionId) {
    return null;
  }

  return (
    <div className="px-3 py-2 border-b border-gray-100 shrink-0 max-h-40 overflow-y-auto">
      <Text strong className="text-xs block mb-1">
        子任务 ({subtasks.length})
      </Text>
      {subtasks.length === 0 ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="暂无委派子任务"
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
                <div className="flex items-center gap-2 min-w-0">
                  <Tag
                    color={STATUS_COLOR[item.status] ?? "default"}
                    className="text-xs shrink-0"
                  >
                    {item.status}
                  </Tag>
                  <Text className="text-xs truncate">{item.title}</Text>
                </div>
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
  );
};

export default CodingSubtaskPanel;
