package com.digital.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 闪卡测试提交历史（一次 submit 生成一条记录，可追溯）
 *
 * 对应表：flashcard_test_attempt
 */
@TableName("flashcard_test_attempt")
@Data
public class FlashCardTestAttempt implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long testId;

    /**
     * 本次提交总分
     */
    private Integer totalScore;

    /**
     * 是否通过（>=60）
     */
    private Integer pass;

    /**
     * AI 学习建议（本次提交）
     */
    private String aiAdvice;

    /**
     * 逐题批改明细快照（JSON）
     */
    private String questionResultsJson;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}

