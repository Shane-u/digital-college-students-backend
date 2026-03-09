package com.digital.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 学习路径→闪卡图谱匹配结果
 */
@Data
public class LearningPathFlashcardMatchVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String pathId;

    private String clickedNodeId;

    /**
     * 最终用于检索的关键词（按权重排序后的 Top tokens）
     */
    private List<Keyword> keywords;

    /**
     * 命中并“点亮”的闪卡 id 列表
     */
    private List<String> matchedFlashcardIds;

    /**
     * 命中的分数（可用于前端做高亮强度）
     */
    private Map<String, Double> scoreMap;

    /**
     * 调试用：返回的 Top hits（未必全部达阈值）
     */
    private List<Hit> topHits;

    @Data
    public static class Keyword implements Serializable {
        private static final long serialVersionUID = 1L;
        private String token;
        private Integer weight;
    }

    @Data
    public static class Hit implements Serializable {
        private static final long serialVersionUID = 1L;
        private String id;
        private String title;
        private Double score;
    }
}

