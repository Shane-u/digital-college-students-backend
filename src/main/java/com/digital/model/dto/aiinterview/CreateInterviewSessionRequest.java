package com.digital.model.dto.aiinterview;

import java.io.Serializable;
import lombok.Data;

@Data
public class CreateInterviewSessionRequest implements Serializable {

    private Long resumeId;

    /**
     * BEHAVIORAL/TECHNICAL/CODING/MIXED
     */
    private String interviewType;

    /**
     * zh-CN/en-US
     */
    private String language;

    /**
     * JUNIOR/MID/SENIOR
     */
    private String difficulty;

    /**
     * 人格/风格，如 strict/mentor/hr 等
     */
    private String persona;

    private Integer durationMinutes;

    private Boolean enableCoding;

    private Boolean enableRealtimeHints;

    private static final long serialVersionUID = 1L;
}

