import React, { useState } from 'react';
import {
  RobotOutlined,
  CodeOutlined,
  MessageOutlined,
  DatabaseOutlined,
  PlusOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import { Tabs, type TabsProps, Button, List, Typography, Popconfirm } from 'antd';
import { useNavigate } from 'react-router-dom';
import AgentTabContent from './tabs/AgentTabContent.tsx';
import AddAgentModal from './modals/AddAgentModal.tsx';
import ChatTabContent from './tabs/ChatTabContent.tsx';
import KnowledgeBaseTabContent from './tabs/KnowledgeBaseTabContent.tsx';
import AddKnowledgeBaseModal from './modals/AddKnowledgeBaseModal.tsx';
import { useAgents } from '../hooks/useAgents.ts';
import { useKnowledgeBases } from '../hooks/useKnowledgeBases.ts';
import { getChatSessions, deleteChatSession } from '../api/api.ts';

const { Text } = Typography;

const SideMenu: React.FC = () => {
  const navigate = useNavigate();
  const [isAddAgentModalOpen, setIsAddAgentModalOpen] = useState(false);
  const [editingAgent, setEditingAgent] = useState<any>(null);
  const [isAddKnowledgeBaseModalOpen, setIsAddKnowledgeBaseModalOpen] = useState(false);
  const { agents, createAgentHandle, deleteAgentHandle, updateAgentHandle } = useAgents();
  const { knowledgeBases, createKnowledgeBaseHandle } = useKnowledgeBases();
  const [codingSessions, setCodingSessions] = useState<any[]>([]);

  const refreshCodingSessions = React.useCallback(() => {
    getChatSessions('CODING').then(r => setCodingSessions(r.chatSessions || [])).catch(() => {});
  }, []);
  React.useEffect(() => { refreshCodingSessions(); }, [refreshCodingSessions]);

  const [activeKey, setActiveKey] = useState(() => {
    if (location.pathname.startsWith('/knowledge-base')) return 'knowledgeBase';
    if (location.pathname.startsWith('/coding')) return 'chat-coding';
    return 'chat-coding';
  });

  const [subKey, setSubKey] = useState(() => {
    if (location.pathname.startsWith('/coding')) return 'coding';
    return 'chat';
  });

  const handleTabChange = (key: string) => { setActiveKey(key); };

  // CSS class to make Ant Design Tabs content fill available height
  const tabCss = 'h-full flex flex-col [&_.ant-tabs-content-holder]:flex-1 [&_.ant-tabs-content-holder]:min-h-0 [&_.ant-tabs-content]:h-full [&_.ant-tabs-tabpane]:h-full';

  const items: TabsProps['items'] = [
    {
      key: 'chat-coding',
      label: <span className='select-none'><MessageOutlined /> 对话与编码</span>,
      children: (
        <div className='flex flex-col h-full'>
          <div className='flex border-b border-gray-200 shrink-0'>
            <Button
              type={subKey === 'chat' ? 'primary' : 'text'}
              size='small'
              style={{ flex: 1, borderRadius: 0 }}
              icon={<RobotOutlined />}
              onClick={() => { setSubKey('chat'); navigate('/chat'); }}
            >
              普通对话
            </Button>
            <Button
              type={subKey === 'coding' ? 'primary' : 'text'}
              size='small'
              style={{ flex: 1, borderRadius: 0 }}
              icon={<CodeOutlined />}
              onClick={() => { setSubKey('coding'); navigate('/coding'); }}
            >
              AI Coding
            </Button>
          </div>
          <div className='flex-1 min-h-0 h-full overflow-y-auto'>
            {subKey === 'chat' && (
              <>
                <div className='px-2'>
                  <AgentTabContent
                    agents={agents}
                    onSelectAgent={(agentId) => { navigate('/chat'); }}
                    onCreateAgentClick={() => setIsAddAgentModalOpen(true)}
                    onEditAgent={(agent) => { setEditingAgent(agent); setIsAddAgentModalOpen(true); }}
                    onDeleteAgent={deleteAgentHandle}
                  />
                </div>
                <div className='px-2'>
                  <ChatTabContent />
                </div>
              </>
            )}
            {subKey === 'coding' && (
              <div className='p-2 flex flex-col gap-2'>
                <Button
                  type='primary'
                  icon={<PlusOutlined />}
                  block
                  onClick={() => { refreshCodingSessions(); navigate('/coding'); }}
                >
                  新建 Coding 会话
                </Button>
                <Text type='secondary' className='text-xs'>已有 Coding 会话</Text>
                <List
                  size='small'
                  dataSource={codingSessions}
                  renderItem={(s: any) => (
                    <List.Item
                      className='cursor-pointer hover:bg-gray-50 rounded px-2 group'
                      onClick={() => { refreshCodingSessions(); navigate('/coding/' + s.id); }}
                      actions={[
                        <Popconfirm key='del' title='删除此 Coding 会话？' description='删除后无法恢复'
                          onConfirm={() => { deleteChatSession(s.id).then(refreshCodingSessions); }}
                          okText='确定' cancelText='取消'
                        >
                          <Button type='text' size='small' icon={<DeleteOutlined />} danger
                            className='opacity-0 group-hover:opacity-100'
                            onClick={(e) => e.stopPropagation()}
                          />
                        </Popconfirm>
                      ]}
                    >
                      <List.Item.Meta title={<Text className='text-sm'>{s.title || 'Coding 会话'}</Text>} />
                    </List.Item>
                  )}
                  locale={{ emptyText: <Text type='secondary'>暂无 Coding 会话</Text> }}
                />
              </div>
            )}
          </div>
        </div>
      ),
    },
    {
      key: 'knowledgeBase',
      label: <span className='select-none'><DatabaseOutlined /> 知识库</span>,
      children: (
        <KnowledgeBaseTabContent
          knowledgeBases={knowledgeBases}
          onCreateKnowledgeBaseClick={() => setIsAddKnowledgeBaseModalOpen(true)}
          onSelectKnowledgeBase={(id) => navigate('/knowledge-base/' + id)}
        />
      ),
    },
  ];

  return (
    <div className='px-4 flex flex-col h-full'>
      <div className='h-14 w-full flex items-center shrink-0'>
        <div className='flex items-center gap-2.5 mx-4'>
          <RobotOutlined className='text-xl text-indigo-600' />
          <div className='text-lg font-semibold select-none text-gray-900'>JChatMind</div>
        </div>
      </div>
      <div className='flex-1 min-h-0 flex flex-col border-t border-gray-200'>
        <Tabs activeKey={activeKey} onChange={handleTabChange} items={items} className={tabCss} />
      </div>
      <AddAgentModal
        open={isAddAgentModalOpen}
        onClose={() => setIsAddAgentModalOpen(false)}
        createAgentHandle={createAgentHandle}
        updateAgentHandle={updateAgentHandle}
        editingAgent={editingAgent}
      />
      <AddKnowledgeBaseModal
        open={isAddKnowledgeBaseModalOpen}
        onClose={() => setIsAddKnowledgeBaseModalOpen(false)}
        createKnowledgeBaseHandle={createKnowledgeBaseHandle}
      />
    </div>
  );
};

export default SideMenu;