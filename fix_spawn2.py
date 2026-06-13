import sys
p = "Z:/JAVA_workshop/JChatMindv2/JChatMind/jchatmind/src/main/java/com/kama/jchatmind/agent/tools/coding/SpawnAgentTool.java"
with open(p, "r", encoding="utf-8") as f:
    lines = f.readlines()

amp = chr(38)
bar = chr(124)

new_lines = []
for line in lines:
    if "import lombok.RequiredArgsConstructor;" in line:
        continue
    if "import org.springframework.context.annotation.Lazy;" in line:
        continue
    if "private final @Lazy JChatMindFactory jChatMindFactory;" in line:
        new_lines.append("    private JChatMindFactory jChatMindFactory;\n")
        continue
    if "import com.kama.jchatmind.agent.JChatMindFactory;" in line:
        new_lines.append(line)
        new_lines.append("import org.springframework.beans.factory.annotation.Autowired;\n")
        new_lines.append("import org.springframework.context.ApplicationContext;\n")
        new_lines.append("import jakarta.annotation.PostConstruct;\n")
        continue
    # Fix the return line with && 
    if "result != null " + amp + amp + " !result.isEmpty()" in line:
        line = line.replace(
            "result != null " + amp + amp + " !result.isEmpty()",
            "result != null " + amp + amp " !result.isEmpty()"
        )
    new_lines.append(line)

with open(p, "w", encoding="utf-8") as f:
    f.writelines(new_lines)

# Now re-read and add PostConstruct + fix constructor
c = open(p, "r", encoding="utf-8").read()

# Replace constructor
old = "public class SpawnAgentTool implements Tool {\n\n    private JChatMindFactory jChatMindFactory;\n    private final AgentMapper agentMapper;\n\n    @Override"
new = "public class SpawnAgentTool implements Tool {\n\n    @Autowired\n    private ApplicationContext applicationContext;\n\n    private JChatMindFactory jChatMindFactory;\n\n    private final AgentMapper agentMapper;\n\n    public SpawnAgentTool(AgentMapper agentMapper) {\n        this.agentMapper = agentMapper;\n    }\n\n    @PostConstruct\n    public void init() {\n        this.jChatMindFactory = applicationContext.getBean(JChatMindFactory.class);\n    }\n\n    @Override"
c = c.replace(old, new)

with open(p, "w", encoding="utf-8") as f:
    f.write(c)
print("done")