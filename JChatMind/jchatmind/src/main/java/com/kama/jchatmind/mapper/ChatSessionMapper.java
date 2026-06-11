package com.kama.jchatmind.mapper;
import com.kama.jchatmind.model.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ChatSessionMapper {
    int insert(ChatSession chatSession);
    int insertWithId(ChatSession chatSession);
    ChatSession selectById(String id);
    List<ChatSession> selectAll();
    List<ChatSession> selectByAgentId(String agentId);
    List<ChatSession> selectByType(String type);
    int deleteById(String id);
    int updateById(ChatSession chatSession);
}