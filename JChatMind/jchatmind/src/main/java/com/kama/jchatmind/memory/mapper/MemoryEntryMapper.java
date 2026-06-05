package com.kama.jchatmind.memory.mapper;

import com.kama.jchatmind.memory.model.entity.MemoryEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MemoryEntryMapper {

    int insert(MemoryEntry entry);

    MemoryEntry selectById(@Param("id") String id);

    /**
     * WORKING 层滑动窗口：取某层级最近 N 条（按时间倒序取 N 再正序返回）。
     */
    List<MemoryEntry> selectRecentByType(@Param("sessionId") String sessionId,
                                         @Param("memoryType") String memoryType,
                                         @Param("limit") int limit);

    /**
     * RECENT 层按重要性召回 top N。
     */
    List<MemoryEntry> selectByImportance(@Param("sessionId") String sessionId,
                                         @Param("memoryType") String memoryType,
                                         @Param("minImportance") Integer minImportance,
                                         @Param("limit") int limit);

    /**
     * 归档候选：某层级中 updated_at 早于指定时间的条目。
     */
    List<MemoryEntry> selectIdleByType(@Param("sessionId") String sessionId,
                                       @Param("memoryType") String memoryType,
                                       @Param("before") LocalDateTime before,
                                       @Param("limit") int limit);

    long countByType(@Param("sessionId") String sessionId,
                     @Param("memoryType") String memoryType);

    int updateById(MemoryEntry entry);

    int updateSummary(@Param("id") String id, @Param("summary") String summary);

    int updateImportanceAndTags(@Param("id") String id,
                                @Param("importance") Integer importance,
                                @Param("tags") List<String> tags);

    int updateMemoryType(@Param("id") String id, @Param("memoryType") String memoryType);

    int archive(@Param("id") String id, @Param("archivedAt") LocalDateTime archivedAt);

    int deleteById(@Param("id") String id);
}
