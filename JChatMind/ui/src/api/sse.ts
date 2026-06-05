/**
 * 解析 API / SSE 基址。开发环境默认走 Vite 代理（/api、/sse）。
 */
export function resolveApiBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL;
  if (configured && String(configured).trim()) {
    return String(configured).replace(/\/$/, "");
  }
  return "/api";
}

export function resolveSseBaseUrl(): string {
  const configured = import.meta.env.VITE_SSE_BASE_URL;
  if (configured && String(configured).trim()) {
    return String(configured).replace(/\/$/, "");
  }
  const apiBase = resolveApiBaseUrl();
  if (apiBase.startsWith("http")) {
    return apiBase.replace(/\/api\/?$/, "");
  }
  return "";
}

export function buildSseConnectUrl(sessionId: string): string {
  const base = resolveSseBaseUrl();
  const path = `/sse/connect/${sessionId}`;
  return base ? `${base}${path}` : path;
}

export function createSseConnection(sessionId: string): EventSource {
  return new EventSource(buildSseConnectUrl(sessionId));
}
