import React, { useCallback, useEffect, useState } from "react";
import { Tree, Spin } from "antd";
import type { DataNode, EventDataNode } from "antd/es/tree";
import { FileOutlined, FolderOutlined } from "@ant-design/icons";
import { getCodingTaskTree, type FileNodeVO } from "../../api/api.ts";

interface CodingFileTreeProps {
  taskId: string;
  selectedPath?: string;
  onSelectFile: (relativePath: string) => void;
  refreshToken?: number;
}

function nodesToTreeData(nodes: FileNodeVO[]): DataNode[] {
  return nodes.map((node) => ({
    title: node.name,
    key: node.relativePath,
    isLeaf: !node.directory,
    icon: node.directory ? <FolderOutlined /> : <FileOutlined />,
  }));
}

function updateTreeChildren(
  list: DataNode[],
  key: React.Key,
  children: DataNode[],
): DataNode[] {
  return list.map((node) => {
    if (node.key === key) {
      return { ...node, children };
    }
    if (node.children) {
      return {
        ...node,
        children: updateTreeChildren(node.children, key, children),
      };
    }
    return node;
  });
}

const CodingFileTree: React.FC<CodingFileTreeProps> = ({
  taskId,
  selectedPath,
  onSelectFile,
  refreshToken = 0,
}) => {
  const [treeData, setTreeData] = useState<DataNode[]>([]);
  const [loading, setLoading] = useState(false);

  const loadRoot = useCallback(async () => {
    setLoading(true);
    try {
      const nodes = await getCodingTaskTree(taskId, ".");
      setTreeData(nodesToTreeData(nodes));
    } finally {
      setLoading(false);
    }
  }, [taskId]);

  useEffect(() => {
    loadRoot().catch(() => undefined);
  }, [loadRoot, refreshToken]);

  const onLoadData = async (treeNode: EventDataNode<DataNode>) => {
    if (treeNode.children && treeNode.children.length > 0) {
      return;
    }
    const path = String(treeNode.key);
    const nodes = await getCodingTaskTree(taskId, path);
    setTreeData((prev) =>
      updateTreeChildren(prev, treeNode.key, nodesToTreeData(nodes)),
    );
  };

  return (
    <div className="h-full flex flex-col bg-gray-50 min-h-0">
      <div className="flex-1 overflow-auto p-2 min-h-0">
        {loading ? (
          <div className="flex justify-center py-8">
            <Spin size="small" />
          </div>
        ) : (
          <Tree
            showIcon
            blockNode
            loadData={onLoadData}
            treeData={treeData}
            selectedKeys={selectedPath ? [selectedPath] : []}
            onSelect={(keys, info) => {
              if (!info.node.isLeaf) return;
              const key = keys[0];
              if (key && typeof key === "string") {
                onSelectFile(key);
              }
            }}
          />
        )}
      </div>
    </div>
  );
};

export default CodingFileTree;
