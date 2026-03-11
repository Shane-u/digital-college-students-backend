package com.digital.model.vo.aiinterview;

import java.io.Serializable;
import lombok.Data;

@Data
public class ResumeUploadVO implements Serializable {

    private Long resumeId;

    private String fileUrl;

    /**
     * 解析是否成功（初版：只要能抽取文本就算成功）
     */
    private Boolean parsed;

    /**
     * 解析摘要（可选）
     */
    private String preview;

    private static final long serialVersionUID = 1L;
}

