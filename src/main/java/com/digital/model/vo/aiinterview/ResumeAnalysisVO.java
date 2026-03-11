package com.digital.model.vo.aiinterview;

import java.io.Serializable;
import lombok.Data;

@Data
public class ResumeAnalysisVO implements Serializable {

    private Long analysisId;

    private Long resumeId;

    /**
     * 分析 JSON
     */
    private String analysisJson;

    private static final long serialVersionUID = 1L;
}

