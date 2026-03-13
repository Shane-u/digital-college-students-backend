package com.digital.model.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 闪卡测试批改结果 VO
 */
@Data
@Builder
public class FlashCardTestResultVO implements Serializable {

    /**
     * 本次提交 ID（attemptId，可追溯）
     */
    private Long submitId;

    /**
     * 测试 ID
     */
    private Long testId;

    /**
     * 总分
     */
    private Integer totalScore;

    /**
     * 是否通过（>=60 分）
     */
    private Boolean pass;

    /**
     * AI 学习建议
     */
    private String aiAdvice;

    /**
     * 点亮状态（0-未点亮，1-已点亮）
     */
    private Integer litStatus;

    /**
     * 当前节点亮度 / 进度（0-100，一般等于本次得分）
     */
    private Integer litProgress;

    /**
     * 逐题批改明细
     */
    private List<FlashCardTestQuestionResultVO> questionResults;

    private static final long serialVersionUID = 1L;
}

