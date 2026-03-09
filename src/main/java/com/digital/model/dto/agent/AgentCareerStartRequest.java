package com.digital.model.dto.agent;

import lombok.Data;

import java.util.Map;

/**
 * 启动职业规划工作流请求
 */
@Data
public class AgentCareerStartRequest {
    private Long userId;
    private String input;
    /**
     * 职业测评结果
     */
    private Map<String, Object> assessmentResult;
}

