package com.digital.model.dto.aiinterview;

import java.io.Serializable;
import lombok.Data;

@Data
public class ResumeAnalysisRequest implements Serializable {

    private String targetRole;

    private String targetLevel;

    private static final long serialVersionUID = 1L;
}

