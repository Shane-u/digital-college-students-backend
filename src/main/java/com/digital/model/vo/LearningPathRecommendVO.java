package com.digital.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 学习路径 - 推荐知识点列表（供用户拿去问别的 AI）
 */
@Data
public class LearningPathRecommendVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 推荐的知识点/问题列表，以求知口吻表述，便于向其他 AI 提问
     */
    private List<RecommendItem> items;

    @Data
    public static class RecommendItem implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 知识点标题或分类 */
        private String title;
        /** 建议向 AI 提问的问题（求知姿态） */
        private String question;
    }
}
