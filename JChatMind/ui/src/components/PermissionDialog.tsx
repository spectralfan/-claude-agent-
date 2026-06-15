import { useState, useEffect, useCallback } from 'react';
import { eventBridge, type PermissionRequest } from '../ws/event-bridge';
import http from '../api/http';

const BASE_URL = 'http://localhost:8080';

export default function PermissionDialog() {
  const [req, setReq] = useState<PermissionRequest | null>(null);
  const [mode, setMode] = useState<'ask' | 'auto'>('ask');

  useEffect(() => {
    fetch(BASE_URL + '/api/mcp/permission/mode')
      .then(r => r.json())
      .then(d => { if (d.mode) setMode(d.mode); })
      .catch(() => {});
  }, []);

  useEffect(() => {
    eventBridge.bind({
      onPermissionRequested: (data: PermissionRequest) => setReq(data),
    });
    return () => eventBridge.unbind();
  }, []);

  const respond = useCallback(async (decision: string) => {
    if (!req) return;
    try {
      await http.post(BASE_URL + '/api/mcp/permission/respond', {
        toolUseId: req.toolUseId, decision,
      });
    } catch (e) { console.error('Permission respond failed', e); }
    setReq(null);
  }, [req]);

  const toggleMode = useCallback(async () => {
    const next = mode === 'ask' ? 'auto' : 'ask';
    try {
      await http.put(BASE_URL + '/api/mcp/permission/mode', { mode: next });
      setMode(next);
    } catch (e) { console.error('toggle mode failed', e); }
  }, [mode]);

  if (!req) return null;

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 9999,
      display: 'flex', justifyContent: 'center', alignItems: 'center',
      background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(4px)',
    }}>
      <div style={{
        background: '#1e293b', borderRadius: 16, padding: 32, maxWidth: 520, width: '90%',
        boxShadow: '0 20px 60px rgba(0,0,0,0.6)', border: '1px solid rgba(255,255,255,0.08)',
      }}>
        <h2 style={{ margin: '0 0 12px', color: '#f1f5f9', fontSize: 20, fontWeight: 600 }}>
          🔒 工具调用审批
        </h2>

        <div style={{ marginBottom: 16, color: '#94a3b8', fontSize: 14 }}>
          <div style={{ marginBottom: 8 }}>
            <span style={{ color: '#64748b' }}>工具：</span>
            <code style={{ color: '#60a5fa', background: 'rgba(96,165,250,0.1)', padding: '2px 8px', borderRadius: 4 }}>
              {req.toolName}
            </code>
          </div>
          <pre style={{
            background: '#0f172a', borderRadius: 8, padding: 12, fontSize: 13,
            maxHeight: 200, overflow: 'auto', color: '#e2e8f0', whiteSpace: 'pre-wrap', wordBreak: 'break-all',
            margin: 0,
          }}>
            {req.paramPreview}
          </pre>
        </div>

        <div style={{ display: 'flex', gap: 10, justifyContent: 'space-between', alignItems: 'center' }}>
          <button onClick={toggleMode} style={{
            padding: '8px 16px', borderRadius: 8, border: '1px solid rgba(255,255,255,0.1)',
            background: mode === 'auto' ? 'rgba(34,197,94,0.15)' : 'rgba(255,255,255,0.05)',
            color: mode === 'auto' ? '#4ade80' : '#94a3b8', cursor: 'pointer', fontSize: 13, fontWeight: 500,
          }}>
            {mode === 'auto' ? '\u26A1 \u81EA\u52A8\u6A21\u5F0F' : '\uD83D\uDD12 \u8BE2\u95EE\u6A21\u5F0F'}
          </button>
          <div style={{ display: 'flex', gap: 10 }}>
            <button onClick={() => respond('deny_once')} style={{
              padding: '10px 24px', borderRadius: 10, border: '1px solid rgba(255,255,255,0.1)',
              background: 'transparent', color: '#94a3b8', cursor: 'pointer', fontSize: 14, fontWeight: 500,
            }}>
              拒绝
            </button>
            <button onClick={() => respond('allow_once')} style={{
              padding: '10px 24px', borderRadius: 10, border: 'none',
              background: 'linear-gradient(135deg, #3b82f6, #2563eb)', color: '#fff',
              cursor: 'pointer', fontSize: 14, fontWeight: 600,
            }}>
              允许
            </button>
            <button onClick={() => respond('always_allow')} style={{
              padding: '10px 24px', borderRadius: 10, border: 'none',
              background: 'linear-gradient(135deg, #22c55e, #16a34a)', color: '#fff',
              cursor: 'pointer', fontSize: 14, fontWeight: 600,
            }}>
              始终允许
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}