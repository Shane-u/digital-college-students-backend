package com.digital.model.dto.aiinterview;

import java.io.Serializable;
import lombok.Data;

@Data
public class TextAnswerUploadRequest implements Serializable {

    private Long questionId;

    /**
     * 回答时长（秒，可选）
     */
    private Integer durationSeconds;

    /**
     * 识别后的文本答案
     */
    private String textAnswer;

    /**
     * ASR 置信度（0-1，可选）
     */
    private Double asrConfidence;

    private static final long serialVersionUID = 1L;
}

