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
     * 层级标签路径，如 "根/课程/HTML" 或 "根/课程/前端/HTML"
     * 第一级固定为根，第二级可选，第三级可选，第四级可选
     */
    private String hierarchyPath;

    private static final long serialVersionUID = 1L;
}
