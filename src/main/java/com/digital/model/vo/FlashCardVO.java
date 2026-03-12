package com.digital.model.vo;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 记忆闪卡视图对象
 *
 * @author Shane
 */
@Data
public class FlashCardVO implements Serializable {

    /**
     * 闪卡id
     */
    private String id;

    /**
     * 用户id
     */
    private Long userId;

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
     * 原始AI回答内容（用于生成闪卡的原始内容）
     */
    private String originalContent;

    /**
     * 闪卡当前所属的层级路径（用于与 Neo4j 图谱对应）
     * 例如：根/课程/前端/Vue.js
     */
    private String hierarchyPath;

    /**
     * 下次复习时间（遵循艾宾浩斯曲线）
     */
    private Date nextReviewTime;

    /**
     * 复习次数
     */
    private Integer reviewCount;

    /**
     * 最后复习时间
     */
    private Date lastReviewTime;

    /**
     * 难度等级（1-重来，2-困难，3-良好，4-简单）
     */
    private Integer difficultyLevel;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 过期天数（仅用于临时闪卡，表示还有多少天过期）
     */
    private Long expirationDays;

    /**
     * 当前闪卡是否已点亮（0-未点亮，1-已点亮）
     * 数据来源：Neo4j 或最近一次测试结果
     */
    private Integer litStatus;

    /**
     * 最近一次测试得分（0-100）
     */
    private Integer litScore;

    /**
     * 闪卡自身亮度（通常等于分数，预留给前端做渐变/发光强度）
     */
    private Integer litProgress;

    private static final long serialVersionUID = 1L;
}


