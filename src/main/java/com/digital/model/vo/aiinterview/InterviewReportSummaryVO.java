package com.digital.model.vo.aiinterview;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 面试报告列表项（摘要）
 */
@Data
public class InterviewReportSummaryVO implements Serializable {

    private Long reportId;

    private Long sessionId;

    private Long resumeId;

    private Date createTime;

    private Date updateTime;

    private static final long serialVersionUID = 1L;
}

