import { Routes, Route } from 'react-router-dom';
import Layout from '../layout/Layout.tsx';
import Sidebar from '../layout/Sidebar.tsx';
import SideMenu from './SideMenu.tsx';
import Content from '../layout/Content.tsx';
import AgentChatView from './views/AgentChatView.tsx';
import KnowledgeBaseView from './views/KnowledgeBaseView.tsx';
import CodingView from './views/CodingView.tsx';

export default function JChatMindLayout() {
  return (
    <Layout>
      <Sidebar>
        <SideMenu />
      </Sidebar>
      <Content>
        <Routes>
          <Route path='/chat' element={<AgentChatView />} />
          <Route path='/chat/:chatSessionId' element={<AgentChatView />} />
          <Route path='/coding' element={<CodingView />} />
          <Route path='/coding/:sessionId' element={<CodingView />} />
          <Route path='/knowledge-base' element={<KnowledgeBaseView />} />
          <Route path='/knowledge-base/:knowledgeBaseId' element={<KnowledgeBaseView />} />
          <Route path='/' element={<AgentChatView />} />
        </Routes>
      </Content>
    </Layout>
  );
}