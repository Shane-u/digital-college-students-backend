package com.digital.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 题目选项VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionOptionVO {

    /**
     * 选项ID
     */
    private Long id;

    /**
     * 选项标题/文本
     */
    private String title;
}
