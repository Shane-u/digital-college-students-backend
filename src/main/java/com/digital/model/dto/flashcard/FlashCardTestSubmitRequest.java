package com.digital.model.dto.flashcard;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 闪卡测试提交与批改请求
 */
@Data
public class FlashCardTestSubmitRequest implements Serializable {

    /**
     * 测试 ID
     */
    private Long testId;

    /**
     * 作答列表
     */
    private List<FlashCardTestAnswerDTO> answers;

    private static final long serialVersionUID = 1L;
}

