package com.digital.model.vo.aiinterview;

import java.io.Serializable;
import lombok.Data;

@Data
public class QuestionVO implements Serializable {

    private Long questionId;

    private Integer orderNo;

    private String questionType;

    private String questionText;

    /**
     * 可选：TTS 音频 URL（如果 needTtsAudio=true）
     */
    private String audioUrl;

    private static final long serialVersionUID = 1L;
}

