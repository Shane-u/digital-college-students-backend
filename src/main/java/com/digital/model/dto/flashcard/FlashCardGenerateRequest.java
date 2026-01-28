package com.digital.model.dto.flashcard;

import java.io.Serializable;
import lombok.Data;

/**
 * 生成闪卡请求
 *
 * @author Shane
 */
@Data
public class FlashCardGenerateRequest implements Serializable {

    /**
     * AI回答的原始内容
     */
    private String originalContent;

    private static final long serialVersionUID = 1L;
}


