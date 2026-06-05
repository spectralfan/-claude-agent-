package com.kama.jchatmind.agent.memory;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 tool 调用轮次（round）裁剪的消息窗口，避免裁掉 assistant+tool_calls 而留下悬空 tool 消息。
 */
public final class ToolAwareMessageWindowChatMemory implements ChatMemory {

    private final int maxMessages;
    private final boolean pinSystemMessage;
    private final boolean pinFirstUserMessage;
    private final Map<String, List<Message>> conversations = new ConcurrentHashMap<>();

    private ToolAwareMessageWindowChatMemory(Builder builder) {
        this.maxMessages = builder.maxMessages;
        this.pinSystemMessage = builder.pinSystemMessage;
        this.pinFirstUserMessage = builder.pinFirstUserMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        conversations.compute(conversationId, (id, existing) -> {
            List<Message> merged = new ArrayList<>(existing != null ? existing : List.of());
            merged.addAll(messages);
            return trimToWindow(merged);
        });
    }

    @Override
    public List<Message> get(String conversationId) {
        List<Message> messages = conversations.get(conversationId);
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return List.copyOf(trimToWindow(new ArrayList<>(messages)));
    }

    @Override
    public void clear(String conversationId) {
        conversations.remove(conversationId);
    }

    List<Message> trimToWindow(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        if (maxMessages <= 0 || messages.size() <= maxMessages) {
            return new ArrayList<>(messages);
        }

        Set<Integer> pinned = resolvePinnedIndices(messages);
        List<List<Integer>> rounds = partitionRoundIndices(messages, pinned);

        Set<Integer> kept = new HashSet<>(pinned);
        int budget = maxMessages;

        for (int r = rounds.size() - 1; r >= 0; r--) {
            List<Integer> round = rounds.get(r);
            if (kept.size() + round.size() <= budget) {
                kept.addAll(round);
            } else if (kept.size() == pinned.size()) {
                kept.addAll(round);
                break;
            } else {
                break;
            }
        }

        List<Message> result = new ArrayList<>(kept.size());
        for (int i = 0; i < messages.size(); i++) {
            if (kept.contains(i)) {
                result.add(messages.get(i));
            }
        }
        return result;
    }

    private Set<Integer> resolvePinnedIndices(List<Message> messages) {
        Set<Integer> pinned = new HashSet<>();
        boolean firstUserPinned = false;
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (pinSystemMessage && message instanceof SystemMessage) {
                pinned.add(i);
            } else if (pinFirstUserMessage && !firstUserPinned && message instanceof UserMessage) {
                pinned.add(i);
                firstUserPinned = true;
            }
        }
        return pinned;
    }

    /**
     * 将非钉住消息切成 round：assistant+tool_calls 与后续连续 tool 响应不可拆分。
     */
    static List<List<Integer>> partitionRoundIndices(List<Message> messages, Set<Integer> pinned) {
        List<List<Integer>> rounds = new ArrayList<>();
        int i = 0;
        while (i < messages.size()) {
            if (pinned.contains(i)) {
                i++;
                continue;
            }
            Message message = messages.get(i);
            if (message instanceof AssistantMessage assistant
                    && !CollectionUtils.isEmpty(assistant.getToolCalls())) {
                List<Integer> round = new ArrayList<>();
                round.add(i++);
                while (i < messages.size() && !pinned.contains(i)
                        && messages.get(i) instanceof ToolResponseMessage) {
                    round.add(i++);
                }
                rounds.add(round);
            } else {
                rounds.add(List.of(i));
                i++;
            }
        }
        return rounds;
    }

    public static final class Builder {
        private int maxMessages = 20;
        private boolean pinSystemMessage = true;
        private boolean pinFirstUserMessage = true;

        public Builder maxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
            return this;
        }

        public Builder pinSystemMessage(boolean pinSystemMessage) {
            this.pinSystemMessage = pinSystemMessage;
            return this;
        }

        public Builder pinFirstUserMessage(boolean pinFirstUserMessage) {
            this.pinFirstUserMessage = pinFirstUserMessage;
            return this;
        }

        public ToolAwareMessageWindowChatMemory build() {
            return new ToolAwareMessageWindowChatMemory(this);
        }
    }
}
