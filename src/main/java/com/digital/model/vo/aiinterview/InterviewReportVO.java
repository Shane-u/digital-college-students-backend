package com.digital.model.vo.aiinterview;

import java.io.Serializable;
import lombok.Data;

@Data
public class InterviewReportVO implements Serializable {

    private Long reportId;

    private Long sessionId;

    private String reportJson;

    private static final long serialVersionUID = 1L;
}

