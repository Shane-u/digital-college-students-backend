package com.digital.model.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 闪卡测试题目返回 VO
 */
@Data
public class FlashCardTestQuestionVO implements Serializable {

    private Long id;

    private String questionType;

    private String content;

    /**
     * 选择题选项
     */
    private List<String> options;

    /**
     * 难度（冗余返回）
     */
    private String difficulty;

    /**
     * 分值
     */
    private Integer score;

    private static final long serialVersionUID = 1L;
}

