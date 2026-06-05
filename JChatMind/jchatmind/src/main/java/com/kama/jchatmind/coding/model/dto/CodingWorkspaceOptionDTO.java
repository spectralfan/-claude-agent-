package com.kama.jchatmind.coding.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 供前端展示/选择的本地工作区（须在服务端白名单内）。
 */
@Data
@Builder
public class CodingWorkspaceOptionDTO {
    /** 展示名，如「订单服务 IDEA 工程」 */
    private String label;
    /** 规范化后的绝对路径 */
    private String path;
    /** 是否为默认工作区 */
    private boolean defaultOption;
}
