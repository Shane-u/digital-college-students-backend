package com.digital.model.dto.question;

import java.io.Serializable;
import lombok.Data;

/**
 * 保存测评结果请求（MBTI/心理测评等）
 */
@Data
public class SaveAssessmentResultRequest implements Serializable {

    /**
     * 测评类型（默认 mbti）
     */
    private String assessmentType = "mbti";

    /**
     * 来源（可选：web/app/import）
     */
    private String source;

    /**
     * 测评结果（任意 JSON 对象/数组，后端会序列化存储）
     */
    private Object result;

    private static final long serialVersionUID = 1L;
}

