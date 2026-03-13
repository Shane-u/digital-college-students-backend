package com.digital.model.vo;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 一套测试题（试卷）摘要 VO
 */
@Data
public class FlashCardTestPaperSummaryVO implements Serializable {

    private Long testId;

    private String nodeId;

    private String difficulty;

    private Integer attemptCount;

    /**
     * 历史最高总分
     */
    private Integer bestTotalScore;

    /**
     * 最近一次提交总分
     */
    private Integer lastTotalScore;

    private Date lastSubmitTime;

    private static final long serialVersionUID = 1L;
}

