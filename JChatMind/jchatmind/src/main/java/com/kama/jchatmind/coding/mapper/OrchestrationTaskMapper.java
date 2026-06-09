package com.kama.jchatmind.coding.mapper;

import com.kama.jchatmind.coding.model.entity.OrchestrationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrchestrationTaskMapper {

    int insert(OrchestrationTask task);

    OrchestrationTask selectById(@Param("id") String id);

    List<OrchestrationTask> selectByParentSession(@Param("parentSessionId") String parentSessionId);

    List<OrchestrationTask> selectByParentSessionAndStatus(
            @Param("parentSessionId") String parentSessionId,
            @Param("status") String status);

    int countRunningByParentSession(@Param("parentSessionId") String parentSessionId);

    int updateStatus(OrchestrationTask task);
}
