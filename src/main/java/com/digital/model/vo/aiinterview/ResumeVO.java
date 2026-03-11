package com.digital.model.vo.aiinterview;

import java.io.Serializable;
import lombok.Data;

@Data
public class ResumeVO implements Serializable {

    private Long resumeId;

    private String fileUrl;

    private String originalFilename;

    private String rawText;

    private String parsedJson;

    private static final long serialVersionUID = 1L;
}

