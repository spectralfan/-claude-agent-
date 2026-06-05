import React from "react";
import { Button, Card, List, Tag, Typography } from "antd";
import type { CodingTaskSummaryVO } from "../../api/api.ts";

const { Text, Paragraph } = Typography;

interface CodingCompletionCardProps {
  summary: CodingTaskSummaryVO;
  onOpenFile?: (path: string) => void;
}

const CodingCompletionCard: React.FC<CodingCompletionCardProps> = ({
  summary,
  onOpenFile,
}) => {
  return (
    <Card
      size="small"
      title="任务交付摘要"
      className="mx-3 mb-2 border-emerald-200 bg-emerald-50/40"
    >
      <div className="flex flex-wrap gap-2 mb-2">
        {summary.stackId && <Tag color="blue">{summary.stackId}</Tag>}
        {summary.language && <Tag>{summary.language}</Tag>}
        <Tag color="success">已完成</Tag>
      </div>
      {summary.resultSummary && (
        <Paragraph className="text-sm whitespace-pre-wrap mb-2">
          {summary.resultSummary}
        </Paragraph>
      )}
      {summary.runInstructions && (
        <Text type="secondary" className="text-xs block mb-2">
          {summary.runInstructions}
        </Text>
      )}
      {summary.changedFiles.length > 0 && (
        <List
          size="small"
          header="变更文件"
          dataSource={summary.changedFiles}
          renderItem={(item) => {
            const path = item.split(" ")[0];
            return (
              <List.Item
                className="py-1!"
                actions={
                  onOpenFile && path
                    ? [
                        <Button
                          key="open"
                          type="link"
                          size="small"
                          onClick={() => onOpenFile(path)}
                        >
                          查看
                        </Button>,
                      ]
                    : undefined
                }
              >
                <Text code className="text-xs">
                  {item}
                </Text>
              </List.Item>
            );
          }}
        />
      )}
    </Card>
  );
};

export default CodingCompletionCard;
