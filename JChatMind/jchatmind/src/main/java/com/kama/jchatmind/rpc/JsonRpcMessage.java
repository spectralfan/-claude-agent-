package com.kama.jchatmind.rpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

public class JsonRpcMessage {
    public static final String JSONRPC_VERSION = "2.0";

    private String jsonrpc = JSONRPC_VERSION;
    private Object id;
    private String method;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object params;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object result;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private JsonRpcError error;

    public JsonRpcMessage() {}

    /** 请求 (Client → Server) */
    public static JsonRpcMessage request(Object id, String method, Object params) {
        JsonRpcMessage m = new JsonRpcMessage(); m.id = id; m.method = method; m.params = params; return m;
    }

    /** 成功响应 (Server → Client) */
    public static JsonRpcMessage response(Object id, Object result) {
        JsonRpcMessage m = new JsonRpcMessage(); m.id = id; m.result = result; return m;
    }

    /** 错误响应 (Server → Client) */
    public static JsonRpcMessage error(Object id, int code, String message) {
        JsonRpcMessage m = new JsonRpcMessage(); m.id = id; m.error = new JsonRpcError(code, message); return m;
    }

    /** 通知 (Server → Client, 无 id) */
    public static JsonRpcMessage notification(String method, Object params) {
        JsonRpcMessage m = new JsonRpcMessage(); m.method = method; m.params = params; return m;
    }

    public String getJsonrpc() { return jsonrpc; }
    public Object getId() { return id; }
    public String getMethod() { return method; }
    public Object getParams() { return params; }
    public Object getResult() { return result; }
    public JsonRpcError getError() { return error; }
    public boolean isRequest() { return id != null && method != null; }
    public boolean isNotification() { return id == null && method != null; }
    public boolean isResponse() { return result != null || error != null; }
}