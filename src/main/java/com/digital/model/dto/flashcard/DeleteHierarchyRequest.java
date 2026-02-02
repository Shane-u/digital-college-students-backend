package com.digital.model.dto.flashcard;

import lombok.Data;

import java.io.Serializable;

/**
 * 删除闪卡层级请求
 */
@Data
public class DeleteHierarchyRequest implements Serializable {

    /**
     * 层级路径，如 "根/课程/HTML"
     */
    private String hierarchyPath;

    private static final long serialVersionUID = 1L;
}