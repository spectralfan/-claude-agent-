import React, { useMemo } from 'react';
import { Button, Divider, Dropdown, Modal } from 'antd';
import type { MenuProps } from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  MoreOutlined,
} from '@ant-design/icons';
import type { AgentVO } from '../../api/api.ts';
import { formatDateTime, getAgentEmoji } from '../../utils';

interface AgentTabContentProps {
  agents: AgentVO[];
  onCreateAgentClick: () => void;
  onSelectAgent: (agentId: string) => void;
  onEditAgent?: (agent: AgentVO) => void;
  onDeleteAgent?: (agentId: string) => void;
}

const SYSTEM_AGENT_NAMES = ['Claude Code Scheduler', 'Claude Code Coding Agent', 'Claude Code Reviewer', 'Claude Code Orchestrator'];

const AgentTabContent: React.FC<AgentTabContentProps> = ({
  agents, onCreateAgentClick, onSelectAgent, onEditAgent, onDeleteAgent
}) => {
  const isSystem = (n: string) => SYSTEM_AGENT_NAMES.includes(n);

  const userAgents = useMemo(() => {
    return agents.filter((a) => !isSystem(a.name)).map((a) => ({ ...a, emoji: getAgentEmoji(a.id) }));
  }, [agents]);

  const getMenuItems = (agent: AgentVO): MenuProps['items'] => {
    if (isSystem(agent.name)) return [];
    const items: MenuProps['items'] = [];
    if (onEditAgent) {
      items.push({ key: 'edit', label: '编辑', icon: <EditOutlined />,
        onClick: (e) => { e.domEvent.stopPropagation(); onEditAgent(agent); } });
    }
    if (onDeleteAgent) {
      items.push({ key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true,
        onClick: (e) => {
          e.domEvent.stopPropagation();
          Modal.confirm({ title: '删除智能体', content: '删除后将无法恢复', okText: '确定', cancelText: '取消', okType: 'danger',
            onOk: () => onDeleteAgent(agent.id) });
        }
      });
    }
    return items;
  };

  return (
    <div className='flex flex-col h-full'>
      <Button color='geekblue' variant='filled' icon={<PlusOutlined />} onClick={onCreateAgentClick} className='w-full'>
        创建智能体
      </Button>
      <Divider />
      <div className='flex-1 overflow-y-auto bg-gray-50 rounded-lg p-1.5'>
        {userAgents.length === 0 ? (
          <div className='flex flex-col items-center justify-center h-full text-gray-400'>
            <p className='text-sm'>暂无自定义智能体</p>
            <p className='text-xs mt-1'>点击上方按钮创建</p>
          </div>
        ) : (
          <div className='space-y-1.5'>
            {userAgents.map((agent: any) => {
              const menuItems = getMenuItems(agent);
              return (
                <div key={agent.id} onClick={() => onSelectAgent(agent.id)}
                  className='w-full px-3 py-3 rounded-lg bg-white cursor-pointer transition-all hover:bg-gray-100 hover:shadow-sm group relative'>
                  <div className='flex items-start gap-3'>
                    <div className='w-8 h-8 rounded-lg bg-gradient-to-br from-yellow-200 to-orange-200 flex items-center justify-center shrink-0 text-lg mt-0.5'>{agent.emoji}</div>
                    <div className='flex-1 min-w-0'>
                      <div className='font-medium text-gray-900 truncate'>{agent.name}</div>
                      {agent.description && <div className='text-xs text-gray-500 mt-1 line-clamp-1'>{agent.description}</div>}
                      {agent.updatedAt && <div className='text-xs text-gray-400 mt-1'>{formatDateTime(agent.updatedAt)}</div>}
                    </div>
                    <div onClick={(e) => e.stopPropagation()} className='opacity-0 group-hover:opacity-100 transition-opacity shrink-0'>
                      <Dropdown menu={{ items: menuItems }} trigger={['contextMenu', 'click']} placement='bottomRight'>
                        <Button type='text' size='small' icon={<MoreOutlined />} className='text-gray-400 hover:text-gray-600' />
                      </Dropdown>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};

export default AgentTabContent;