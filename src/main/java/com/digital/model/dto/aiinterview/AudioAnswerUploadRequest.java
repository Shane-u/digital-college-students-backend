package com.digital.model.dto.aiinterview;

import java.io.Serializable;
import lombok.Data;

@Data
public class AudioAnswerUploadRequest implements Serializable {

    private Long questionId;

    /**
     * 回答时长（秒，可选）
     */
    private Integer durationSeconds;

    private static final long serialVersionUID = 1L;
}

