package com.digital.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 题目VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionVO {

    /**
     * 题目ID
     */
    private Long id;

    /**
     * 题目标题/文本
     */
    private String title;

    /**
     * 题目选项列表
     */
    private List<QuestionOptionVO> options;
}
