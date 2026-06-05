import React, { useState } from "react";
import { Button, Space } from "antd";
import { PaperClipOutlined } from "@ant-design/icons";
import { Sender } from "@ant-design/x";

interface CodingChatInputProps {
  onSend: (message: string) => void;
  selectedFilePath?: string;
  disabled?: boolean;
}

const CodingChatInput: React.FC<CodingChatInputProps> = ({
  onSend,
  selectedFilePath,
  disabled,
}) => {
  const [message, setMessage] = useState("");

  const insertFileRef = () => {
    if (!selectedFilePath) return;
    const token = `@${selectedFilePath}`;
    setMessage((prev) => (prev.includes(token) ? prev : prev ? `${prev} ${token}` : token));
  };

  return (
    <Space direction="vertical" className="w-full" size="small">
      <div className="flex gap-2 items-end">
        <div className="flex-1">
          <Sender
            onSubmit={() => {
              const text = message.trim();
              if (!text) return;
              onSend(text);
              setMessage("");
            }}
            placeholder="描述需求，或用 @文件 引用左侧选中文件…"
            value={message}
            onChange={setMessage}
            disabled={disabled}
          />
        </div>
        <Button
          icon={<PaperClipOutlined />}
          disabled={!selectedFilePath || disabled}
          onClick={insertFileRef}
          title={selectedFilePath ? `引用 @${selectedFilePath}` : "请先在左侧选中文件"}
        >
          @文件
        </Button>
      </div>
    </Space>
  );
};

export default CodingChatInput;
