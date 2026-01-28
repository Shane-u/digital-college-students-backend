package com.digital.model.dto.flashcard;

import java.io.Serializable;
import lombok.Data;

/**
 * AI辅助修改闪卡请求
 *
 * @author Shane
 */
@Data
public class FlashCardAIAssistRequest implements Serializable {

    /**
     * 闪卡id
     */
    private Long id;

    /**
     * 用户自定义提示词
     */
    private String prompt;

    private static final long serialVersionUID = 1L;
}


