package com.digital.model.dto.flashcard;

import java.io.Serializable;
import lombok.Data;

/**
 * 更新闪卡请求
 *
 * @author Shane
 */
@Data
public class FlashCardUpdateRequest implements Serializable {

    /**
     * 闪卡id
     */
    private String id;

    /**
     * 知识点标题
     */
    private String title;

    /**
     * 详细知识点内容（纯文本）
     */
    private String content;

    /**
     * 闪卡HTML内容（包含HTML+CSS+SVG动画）
     */
    private String htmlContent;

    /**
     * 闪卡层级路径（用于更新 Neo4j 中的层级结构）
     * 例如：根/课程/HTML 或 根/课程/前端/HTML
     */
    private String hierarchyPath;

    private static final long serialVersionUID = 1L;
}


