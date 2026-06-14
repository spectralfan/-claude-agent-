import { useEffect, useRef } from 'react';
import { rpcClient } from '../ws/rpc-client';
import type { SseMessage, SseMessageType } from '../types';

export function useSessionSse(
  sessionId: string | undefined,
  onEvent: (msg: SseMessage) => void,
) {
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  useEffect(() => {
    if (!sessionId) return;

    // Connect RPC client to backend
    rpcClient.connect('localhost', 8080);
    rpcClient.setSessionId(sessionId);

    // Map RPC event notifications to SseMessage format
    const eventMap: Record<string, SseMessageType> = {
      'event.run.started': 'AI_THINKING',
      'event.step.started': 'AI_EXECUTING',
      'event.tool.called': 'AI_EXECUTING',
      'event.tool.result': 'AI_OBSERVING',
      'event.run.finished': 'AI_DONE',
      'event.subagent.started': 'SUBAGENT_STARTED',
      'event.subagent.finished': 'SUBAGENT_FINISHED',
    };

    // Track active sub-agents for progress display
    const activeSubAgents = new Map<string, {
      description: string;
      steps: number;
      currentTool: string;
      lastObserve: string;
    }>();

    const eventHandler = (msg: any) => {
      const params = msg.params ?? {};
      const runId = params.runId;
      const parentRunId = params.parentRunId;

      // Track sub-agent progress events by runId
      if (msg.method === 'event.subagent.started' && runId) {
        activeSubAgents.set(runId, {
          description: params.description || 'subtask',
          steps: 0,
          currentTool: '',
          lastObserve: '',
        });
      }
      if (msg.method === 'event.subagent.finished' && runId) {
        activeSubAgents.delete(runId);
      }
      if (msg.method === 'event.step.started' && runId && activeSubAgents.has(runId)) {
        const sa = activeSubAgents.get(runId)!;
        sa.steps++;
        sa.currentTool = '';
      }
      if (msg.method === 'event.tool.called' && runId && activeSubAgents.has(runId)) {
        const sa = activeSubAgents.get(runId)!;
        sa.currentTool = params.toolName || '';
      }
      if (msg.method === 'event.tool.result' && runId && activeSubAgents.has(runId)) {
        const sa = activeSubAgents.get(runId)!;
        sa.lastObserve = (params.result || '').substring(0, 80);
      }

      // Pass sub-agent status in payload for frontend rendering
      if (msg.method === 'event.subagent.started' || msg.method === 'event.subagent.finished') {
        params._activeSubAgents = Array.from(activeSubAgents.entries()).map(([id, info]) => ({
          runId: id,
          ...info,
        }));
      }

      const sseType = eventMap[msg.method];
      if (sseType) {
        onEventRef.current({
          type: sseType,
          payload: params,
          metadata: { sessionId: sessionId ?? '' },
        } as SseMessage);
      }
    };

    const sseHandler = (msg: any) => {
      // sse.* notifications: params = { sessionId, message: SseMessage }
      const params = msg.params as any;
      const msgSessionId = params?.sessionId || '';
      if (msgSessionId && msgSessionId !== sessionId) return;
      const sseMsg = params?.message as SseMessage;
      if (sseMsg && sseMsg.type) {
        onEventRef.current(sseMsg);
      }
    };

    Object.keys(eventMap).forEach((m) => rpcClient.on(m, eventHandler));
    rpcClient.on('sse.*', sseHandler);

    return () => {
      Object.keys(eventMap).forEach((m) => rpcClient.off(m, eventHandler));
      rpcClient.off('sse.*', sseHandler);
    };
  }, [sessionId]);
}