package com.digital.model.dto.aiinterview;

import java.io.Serializable;
import lombok.Data;

@Data
public class ResumeUploadRequest implements Serializable {

    /**
     * 目标岗位（可选）
     */
    private String position;

    /**
     * 经验年限（可选）
     */
    private Double experienceYears;

    private static final long serialVersionUID = 1L;
}

