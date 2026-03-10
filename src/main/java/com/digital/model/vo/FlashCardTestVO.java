package com.digital.model.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 闪卡测试整体返回 VO（题目列表）
 */
@Data
public class FlashCardTestVO implements Serializable {

    /**
     * 测试 ID
     */
    private Long testId;

    /**
     * 关联的节点 ID
     */
    private String nodeId;

    /**
     * 难度
     */
    private String difficulty;

    /**
     * 题目列表
     */
    private List<FlashCardTestQuestionVO> questions;

    private static final long serialVersionUID = 1L;
}

