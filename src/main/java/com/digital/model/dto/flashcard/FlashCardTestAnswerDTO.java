package com.digital.model.dto.flashcard;

import java.io.Serializable;
import lombok.Data;

/**
 * 单题作答 DTO
 */
@Data
public class FlashCardTestAnswerDTO implements Serializable {

    /**
     * 题目 ID
     */
    private Long questionId;

    /**
     * 题目类型：choice / blank / code
     */
    private String questionType;

    /**
     * 用户作答内容（文本）
     */
    private String userAnswer;

    /**
     * 用户上传图片 URL（编程题拍照）
     */
    private String userUploadUrl;

    private static final long serialVersionUID = 1L;
}

