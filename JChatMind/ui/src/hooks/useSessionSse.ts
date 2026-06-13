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
    };

    const eventHandler = (msg: any) => {
      const sseType = eventMap[msg.method];
      if (sseType) {
        onEventRef.current({
          type: sseType,
          payload: msg.params ?? {},
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