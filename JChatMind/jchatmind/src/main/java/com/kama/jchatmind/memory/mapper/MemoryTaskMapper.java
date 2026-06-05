package com.kama.jchatmind.memory.mapper;

import com.kama.jchatmind.memory.model.entity.MemoryTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MemoryTaskMapper {

    int insert(MemoryTask task);

    MemoryTask selectById(@Param("id") String id);

    /**
     * 取待处理任务（按优先级降序、创建时间升序）。供阶段 5-6 的任务轮询使用。
     */
    List<MemoryTask> selectPending(@Param("limit") int limit);

    int updateStatus(@Param("id") String id,
                     @Param("status") String status,
                     @Param("startedAt") LocalDateTime startedAt,
                     @Param("completedAt") LocalDateTime completedAt,
                     @Param("resultData") String resultData,
                     @Param("errorMessage") String errorMessage);
}
