import sys
p = "Z:/JAVA_workshop/JChatMindv2/JChatMind/jchatmind/src/main/java/com/kama/jchatmind/agent/tools/coding/SpawnAgentTool.java"
with open(p, "r", encoding="utf-8") as f:
    c = f.read()

# Replace PostConstruct init with getFactory() method
c = c.replace(
    "import jakarta.annotation.PostConstruct;\nimport lombok.extern.slf4j.Slf4j;\nimport org.springframework.beans.factory.annotation.Autowired;\nimport org.springframework.context.ApplicationContext;\nimport org.springframework.stereotype.Component;",
    "import lombok.extern.slf4j.Slf4j;\nimport org.springframework.beans.factory.annotation.Autowired;\nimport org.springframework.context.ApplicationContext;\nimport org.springframework.stereotype.Component;"
)

c = c.replace(
    "    @Autowired\n    private ApplicationContext applicationContext;\n\n    private JChatMindFactory jChatMindFactory;\n\n    private final AgentMapper agentMapper;\n\n    public SpawnAgentTool(AgentMapper agentMapper) {\n        this.agentMapper = agentMapper;\n    }\n\n    @PostConstruct\n    public void init() {\n        this.jChatMindFactory = applicationContext.getBean(JChatMindFactory.class);\n    }",
    "    @Autowired\n    private ApplicationContext applicationContext;\n\n    private final AgentMapper agentMapper;\n\n    public SpawnAgentTool(AgentMapper agentMapper) {\n        this.agentMapper = agentMapper;\n    }\n\n    private JChatMindFactory getFactory() {\n        return applicationContext.getBean(JChatMindFactory.class);\n    }"
)

c = c.replace(
    "            JChatMind subAgent = jChatMindFactory.createSpawnSubAgent(",
    "            JChatMind subAgent = getFactory().createSpawnSubAgent("
)

with open(p, "w", encoding="utf-8") as f:
    f.write(c)
print("done")