package com.digital.model.vo.aiinterview;

import java.io.Serializable;
import lombok.Data;

@Data
public class AnswerVO implements Serializable {

    private Long answerId;

    private Long questionId;

    private String textAnswer;

    private Double asrConfidence;

    /**
     * 评价 JSON（评分、改进建议等）
     */
    private String evaluationJson;

    private static final long serialVersionUID = 1L;
}

