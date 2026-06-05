package com.kama.jchatmind.agent;

public enum AgentState {
    IDLE,  // 空闲
    PLANNING,  // 计划中
    THINKING,  // 推理中
    EXECUTING, // 工具调用中
    OBSERVING, // 观察工具结果
    FINISHED,  // 正常结束
    ERROR  // 错误结束
}
