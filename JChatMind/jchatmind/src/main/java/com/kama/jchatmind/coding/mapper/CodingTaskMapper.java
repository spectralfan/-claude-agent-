package com.kama.jchatmind.coding.mapper;

import com.kama.jchatmind.coding.model.entity.CodingTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface CodingTaskMapper {

    int insert(CodingTask task);

    CodingTask selectById(@Param("id") String id);

    /**
     * 查找 session 当前活动任务（running / waiting_approval）。
     */
    CodingTask selectActiveBySession(@Param("sessionId") String sessionId);

    int updateStatus(@Param("id") String id,
                     @Param("status") String status,
                     @Param("finishedAt") LocalDateTime finishedAt,
                     @Param("resultSummary") String resultSummary,
                     @Param("approvalReason") String approvalReason);

    int updateCommand(@Param("id") String id,
                      @Param("command") String command,
                      @Param("pendingAction") String pendingAction,
                      @Param("pendingPayload") String pendingPayload);

    int clearPending(@Param("id") String id);

    int recordExecutionResult(@Param("id") String id,
                              @Param("command") String command,
                              @Param("resultSummary") String resultSummary);

    int updateMetadata(@Param("id") String id, @Param("metadata") String metadata);
}
