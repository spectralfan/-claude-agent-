import { useEffect, useRef } from "react";
import { createSseConnection } from "../api/sse.ts";
import type { SseMessage } from "../types";

export function useSessionSse(
  sessionId: string | undefined,
  onEvent: (msg: SseMessage) => void,
) {
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  useEffect(() => {
    if (!sessionId) {
      return;
    }

    const es = createSseConnection(sessionId);

    const handleMessage = (event: MessageEvent) => {
      try {
        const msg = JSON.parse(event.data) as SseMessage;
        onEventRef.current(msg);
      } catch {
        // ignore malformed payload
      }
    };

    es.addEventListener("message", handleMessage);
    es.onerror = () => console.warn("SSE connection error", sessionId);

    return () => es.close();
  }, [sessionId]);
}
