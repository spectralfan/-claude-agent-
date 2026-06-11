import React, { useState } from 'react';
import {
  RobotOutlined,
  CodeOutlined,
  MessageOutlined,
  DatabaseOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { Tabs, type TabsProps, Button, List, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import AgentTabContent from './tabs/AgentTabContent.tsx';
import AddAgentModal from './modals/AddAgentModal.tsx';
import ChatTabContent from './tabs/ChatTabContent.tsx';
import KnowledgeBaseTabContent from './tabs/KnowledgeBaseTabContent.tsx';
import AddKnowledgeBaseModal from './modals/AddKnowledgeBaseModal.tsx';
import { useAgents } from '../hooks/useAgents.ts';
import { useKnowledgeBases } from '../hooks/useKnowledgeBases.ts';
import { getChatSessions } from '../api/api.ts';

const { Text } = Typography;

const SideMenu: React.FC = () => {
  const navigate = useNavigate();
  const [isAddAgentModalOpen, setIsAddAgentModalOpen] = useState(false);
  const [editingAgent, setEditingAgent] = useState<any>(null);
  const [isAddKnowledgeBaseModalOpen, setIsAddKnowledgeBaseModalOpen] = useState(false);
  const { agents, createAgentHandle, deleteAgentHandle, updateAgentHandle } = useAgents();
  const { knowledgeBases, createKnowledgeBaseHandle } = useKnowledgeBases();
  const [codingSessions, setCodingSessions] = useState<any[]>([]);

  React.useEffect(() => {
    getChatSessions('CODING').then(r => setCodingSessions(r.chatSessions || [])).catch(() => {});
  }, []);

  const [activeKey, setActiveKey] = useState(() => {
    if (location.pathname.startsWith('/knowledge-base')) return 'knowledgeBase';
    return 'chat-coding';
  });

  const [subKey, setSubKey] = useState(() => {
    if (location.pathname.startsWith('/coding')) return 'coding';
    return 'chat';
  });

  const handleTabChange = (key: string) => { setActiveKey(key); };

  const items: TabsProps['items'] = [
    {
      key: 'chat-coding',
      label: <span className='select-none'><MessageOutlined /> 对话与编码</span>,
      children: (
        <div className='flex flex-col h-full'>
          <div className='flex border-b border-gray-200 mb-2'>
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
          <div className='flex-1 min-h-0 overflow-y-auto'>
            {subKey === 'chat' && (
              <>
                <AgentTabContent
                  agents={agents}
                  onSelectAgent={(agentId) => {
                    navigate('/chat');
                  }}
                  onCreateAgentClick={() => setIsAddAgentModalOpen(true)}
                  onEditAgent={(agent) => { setEditingAgent(agent); setIsAddAgentModalOpen(true); }}
                  onDeleteAgent={deleteAgentHandle}
                />
                <div className='px-2 mt-1'>
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
                  onClick={() => navigate('/coding')}
                >
                  新建 Coding 会话
                </Button>
                <Text type='secondary' className='text-xs'>已有 Coding 会话</Text>
                <List
                  size='small'
                  dataSource={codingSessions}
                  renderItem={(s: any) => (
                    <List.Item
                      className='cursor-pointer hover:bg-gray-50 rounded px-2'
                      onClick={() => navigate('/coding/' + s.id)}
                    >
                      <List.Item.Meta
                        title={<Text className='text-sm'>{s.title || 'Coding 会话'}</Text>}
                      />
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
      <div className='h-14 w-full flex items-center border-b border-gray-200'>
        <div className='flex items-center gap-2.5 mx-4'>
          <RobotOutlined className='text-xl text-indigo-600' />
          <div className='text-lg font-semibold select-none text-gray-900'>JChatMind</div>
        </div>
      </div>
      <div className='flex-1 min-h-0 flex flex-col'>
        <Tabs activeKey={activeKey} onChange={handleTabChange} items={items} />
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