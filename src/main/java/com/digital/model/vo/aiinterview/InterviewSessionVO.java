package com.digital.model.vo.aiinterview;

import java.io.Serializable;
import lombok.Data;

@Data
public class InterviewSessionVO implements Serializable {

    private Long sessionId;

    private Long resumeId;

    private String status;

    private String welcomeMessage;

    private static final long serialVersionUID = 1L;
}

