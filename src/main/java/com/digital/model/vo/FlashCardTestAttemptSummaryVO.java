package com.digital.model.vo;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 闪卡测试提交历史摘要 VO
 */
@Data
public class FlashCardTestAttemptSummaryVO implements Serializable {

    private Long attemptId;

    private Long testId;

    private Integer totalScore;

    private Boolean pass;

    private Date submitTime;

    private static final long serialVersionUID = 1L;
}

