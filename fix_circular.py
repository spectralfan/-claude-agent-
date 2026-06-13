import sys
p = "Z:/JAVA_workshop/JChatMindv2/JChatMind/jchatmind/src/main/java/com/kama/jchatmind/agent/tools/coding/SpawnAgentTool.java"
with open(p, "r", encoding="utf-8") as f:
    c = f.read()

c = c.replace(
    "import lombok.RequiredArgsConstructor;\nimport lombok.extern.slf4j.Slf4j;\nimport org.springframework.stereotype.Component;",
    "import lombok.RequiredArgsConstructor;\nimport lombok.extern.slf4j.Slf4j;\nimport org.springframework.context.annotation.Lazy;\nimport org.springframework.stereotype.Component;"
)

c = c.replace(
    "    private final JChatMindFactory jChatMindFactory;\n    private final AgentMapper agentMapper;",
    "    private final @Lazy JChatMindFactory jChatMindFactory;\n    private final AgentMapper agentMapper;"
)

with open(p, "w", encoding="utf-8") as f:
    f.write(c)
print("done")