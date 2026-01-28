package com.digital.model.dto.flashcard;

import java.io.Serializable;
import lombok.Data;

/**
 * 复习闪卡请求
 *
 * @author Shane
 */
@Data
public class FlashCardReviewRequest implements Serializable {

    /**
     * 闪卡id
     */
    private Long id;

    /**
     * 难度等级（1-重来，2-困难，3-良好，4-简单）
     */
    private Integer difficultyLevel;

    private static final long serialVersionUID = 1L;
}


