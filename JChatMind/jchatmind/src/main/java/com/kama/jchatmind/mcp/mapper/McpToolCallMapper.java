package com.kama.jchatmind.mcp.mapper;

import com.kama.jchatmind.mcp.model.entity.McpToolCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface McpToolCallMapper {

    int insert(McpToolCall call);

    /**
     * 调用历史：可选按 serverId / toolName 过滤，since 之后（含）的记录，按时间倒序。
     */
    List<McpToolCall> selectHistory(@Param("serverId") String serverId,
                                    @Param("toolName") String toolName,
                                    @Param("since") LocalDateTime since,
                                    @Param("limit") int limit);

    /**
     * 用量统计：某 server 下各 tool 的调用次数。返回 [{tool_name, cnt}]。
     */
    List<Map<String, Object>> usageStats(@Param("serverId") String serverId);
}
