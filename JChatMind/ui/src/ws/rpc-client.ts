// JSON-RPC 2.0 WebSocket Client

export interface JsonRpcRequest {
  jsonrpc: "2.0";
  id: number;
  method: string;
  params?: any;
}

export interface JsonRpcResponse {
  jsonrpc: "2.0";
  id: number;
  result?: any;
  error?: { code: number; message: string };
}

export interface JsonRpcNotification {
  jsonrpc: "2.0";
  method: string;
  params?: any;
}

type MessageHandler = (msg: JsonRpcNotification) => void;

class RpcClient {
  private ws: WebSocket | null = null;
  private nextId = 1;
  private pending = new Map<number, { resolve: (v: any) => void; reject: (e: Error) => void }>();
  private handlers = new Map<string, MessageHandler[]>();
  private url = "";
  private reconnectTimer: any = null;
  private sessionId = "";

  connect(host?: string, port?: number) {
    const h = host || window.location.hostname || "localhost";
    const p = port || 8080;
    this.url = "ws://" + h + ":" + p + "/ws/rpc";
    this.doConnect();
  }

  private doConnect() {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) return;
    try { this.ws = new WebSocket(this.url); } catch { return; }
    this.ws.onopen = () => {
      console.log("[RPC] connected");
      if (this.sessionId) this.send("session.bind", { sessionId: this.sessionId });
    };
    this.ws.onmessage = (e) => { try { this.handleMessage(JSON.parse(e.data)); } catch {} };
    this.ws.onclose = () => {
      console.log("[RPC] disconnected, reconnect in 3s");
      this.ws = null; this.rejectAll(new Error("Connection closed"));
      this.reconnectTimer = setTimeout(() => this.doConnect(), 3000);
    };
    this.ws.onerror = () => { this.ws?.close(); };
  }

  private handleMessage(data: any) {
    if (data.method && !data.id) {
      const handlers = this.handlers.get(data.method) || [];
      handlers.forEach((h) => h(data));
      const wildcard = this.handlers.get("*") || [];
      wildcard.forEach((h) => h(data));
      const dotIdx = data.method.lastIndexOf(".");
      if (dotIdx > 0) {
        const prefix = data.method.substring(0, dotIdx) + ".*";
        const ph = this.handlers.get(prefix) || [];
        ph.forEach((h) => h(data));
      }
      return;
    }
    if (data.id !== undefined) {
      const pending = this.pending.get(data.id);
      if (!pending) return;
      this.pending.delete(data.id);
      if (data.error) pending.reject(new Error(data.error.message));
      else pending.resolve(data.result);
    }
  }

  send(method: string, params?: any): Promise<any> {
    return new Promise((resolve, reject) => {
      const id = this.nextId++;
      const msg: JsonRpcRequest = { jsonrpc: "2.0", id, method, params };
      this.pending.set(id, { resolve, reject });
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify(msg));
      } else { this.pending.delete(id); reject(new Error("Not connected")); }
    });
  }

  on(method: string, handler: MessageHandler) {
    const list = this.handlers.get(method) || [];
    list.push(handler);
    this.handlers.set(method, list);
  }

  off(method: string, handler: MessageHandler) {
    const list = this.handlers.get(method) || [];
    this.handlers.set(method, list.filter((h) => h !== handler));
  }

  setSessionId(sid: string) {
    this.sessionId = sid;
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.send("session.bind", { sessionId: sid });
    }
  }

  disconnect() {
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    if (this.ws) { this.ws.close(); this.ws = null; }
    this.rejectAll(new Error("Disconnected"));
  }

  private rejectAll(err: Error) {
    this.pending.forEach((p) => p.reject(err));
    this.pending.clear();
  }
}

export const rpcClient = new RpcClient();