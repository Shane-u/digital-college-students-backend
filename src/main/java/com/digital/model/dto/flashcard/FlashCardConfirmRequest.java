package com.digital.model.dto.flashcard;

import java.io.Serializable;
import lombok.Data;

/**
 * 确认保存闪卡请求
 *
 * @author Shane
 */
@Data
public class FlashCardConfirmRequest implements Serializable {

    /**
     * 临时闪卡ID
     */
    private String id;

    /**
     * 层级标签路径，如 "root/课程/HTML" 或 "root/课程/前端/HTML"
     * 前两级固定（root/课程），第三级可选，第四级可选
     */
    private String hierarchyPath;

    private static final long serialVersionUID = 1L;
}
