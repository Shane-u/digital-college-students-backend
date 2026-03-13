package com.digital.model.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 闪卡测试逐题批改明细 VO
 */
@Data
public class FlashCardTestQuestionResultVO implements Serializable {

    private Long id;

    private String questionType;

    private String content;

    /**
     * 选择题选项
     */
    private List<String> options;

    /**
     * 标准答案/参考代码
     */
    private String answer;

    /**
     * 题目满分
     */
    private Integer score;

    /**
     * 本题得分
     */
    private Integer userScore;

    /**
     * 用户作答（文本）
     */
    private String userAnswer;

    /**
     * 用户上传图片 URL（编程题拍照）
     */
    private String userUploadUrl;

    /**
     * AI 批改评语/解析
     */
    private String aiAnswer;

    private static final long serialVersionUID = 1L;
}

