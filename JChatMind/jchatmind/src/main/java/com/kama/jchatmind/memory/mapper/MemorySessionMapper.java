package com.kama.jchatmind.memory.mapper;

import com.kama.jchatmind.memory.model.entity.MemorySession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface MemorySessionMapper {

    int insert(MemorySession session);

    MemorySession selectBySessionId(@Param("sessionId") String sessionId);

    int updateActivity(@Param("sessionId") String sessionId,
                       @Param("lastActivityAt") LocalDateTime lastActivityAt);

    int incrementCounters(@Param("sessionId") String sessionId,
                          @Param("messageDelta") int messageDelta,
                          @Param("tokenDelta") int tokenDelta);

    int updateStatus(@Param("sessionId") String sessionId, @Param("status") String status);
}
