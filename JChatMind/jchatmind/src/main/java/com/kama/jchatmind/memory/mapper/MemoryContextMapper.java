package com.kama.jchatmind.memory.mapper;

import com.kama.jchatmind.memory.model.entity.MemoryContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MemoryContextMapper {

    int insert(MemoryContext context);

    MemoryContext selectBySessionAndKey(@Param("sessionId") String sessionId,
                                        @Param("contextKey") String contextKey);

    List<MemoryContext> selectBySession(@Param("sessionId") String sessionId);

    int updateValue(@Param("sessionId") String sessionId,
                    @Param("contextKey") String contextKey,
                    @Param("contextValue") String contextValue,
                    @Param("metadata") String metadata);

    int deleteBySessionAndKey(@Param("sessionId") String sessionId,
                              @Param("contextKey") String contextKey);
}
