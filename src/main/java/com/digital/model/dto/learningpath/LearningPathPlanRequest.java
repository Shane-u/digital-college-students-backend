package com.digital.model.dto.learningpath;

import lombok.Data;

import java.io.Serializable;

/**
 * 孪孪伴学 - 规划学习路径请求
 * 流式调用：入参是用户提示词、当前的学习路径（可为空）
 *
 * @author Shane
 */
@Data
public class LearningPathPlanRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户提示词，如："当前想要学习 Java，请给出学习 Java 的学习路径。"
     */
    private String userPrompt;

    /**
     * 当前的学习路径 JSON（润色/修改时传入，首次生成可为空）
     */
    private String currentPathJson;

    /**
     * 用户 ID（可选，若未登录可从 session 获取）
     */
    private Long userId;
}
