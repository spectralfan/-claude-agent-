import { rpcClient } from './rpc-client';
import type { ChatMessageVO } from '../api/api';

export type PermissionRequest = {
  toolUseId: string;
  toolName: string;
  paramPreview: string;
  sessionId: string;
  ts: string;
};

export type EventCallback = {
  onMessage?: (msg: ChatMessageVO) => void;
  onRunStarted?: (data: any) => void;
  onRunFinished?: (data: any) => void;
  onStepStarted?: (data: any) => void;
  onToolCalled?: (data: any) => void;
  onToolResult?: (data: any) => void;
  onPermissionRequested?: (data: PermissionRequest) => void;
  onError?: (err: string) => void;
};

class EventBridge {
  private callbacks: EventCallback = {};
  private bound: string[] = [];

  bind(cbs: EventCallback) {
    this.callbacks = cbs;
    this.bound.forEach((m) => rpcClient.off(m, this.handle));
    this.bound = [
      'event.run.started',
      'event.run.finished',
      'event.step.started',
      'event.tool.called',
      'event.tool.result',
      'event.permission.requested',
    ];
    this.bound.forEach((m) => rpcClient.on(m, this.handle));
  }

  unbind() {
    this.bound.forEach((m) => rpcClient.off(m, this.handle));
    this.bound = [];
    this.callbacks = {};
  }

  private handle = (msg: any) => {
    const cb = this.callbacks;
    switch (msg.method) {
      case 'event.run.started': cb.onRunStarted?.(msg.params); break;
      case 'event.run.finished': cb.onRunFinished?.(msg.params); break;
      case 'event.step.started': cb.onStepStarted?.(msg.params); break;
      case 'event.tool.called': cb.onToolCalled?.(msg.params); break;
      case 'event.tool.result': cb.onToolResult?.(msg.params); break;
      case 'event.permission.requested': cb.onPermissionRequested?.(msg.params); break;
    }
  };

  connect(host: string, port: number) {
    rpcClient.connect(host, port);
  }

  setSessionId(sid: string) {
    rpcClient.setSessionId(sid);
  }
}

export const eventBridge = new EventBridge();