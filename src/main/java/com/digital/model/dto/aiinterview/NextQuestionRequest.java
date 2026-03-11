package com.digital.model.dto.aiinterview;

import java.io.Serializable;
import lombok.Data;

@Data
public class NextQuestionRequest implements Serializable {

    /**
     * 是否需要 TTS 音频（可选，默认 false）
     */
    private Boolean needTtsAudio;

    private static final long serialVersionUID = 1L;
}

