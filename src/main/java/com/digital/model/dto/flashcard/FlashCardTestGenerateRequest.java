package com.digital.model.dto.flashcard;

import java.io.Serializable;
import lombok.Data;

/**
 * 闪卡测试生成请求
 */
@Data
public class FlashCardTestGenerateRequest implements Serializable {

    /**
     * 闪卡或学习节点 ID（与 Neo4j / FlashCard 对齐）
     */
    private String nodeId;

    /**
     * 学习路径 ID（可选）
     */
    private Long pathId;

    /**
     * 难度：easy / medium / hard
     */
    private String difficulty;

    private static final long serialVersionUID = 1L;
}

