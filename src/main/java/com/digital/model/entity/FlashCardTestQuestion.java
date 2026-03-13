package com.digital.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 闪卡测试题目记录
 *
 * 对应表：flashcard_test_question
 */
@TableName("flashcard_test_question")
@Data
public class FlashCardTestQuestion implements Serializable {

    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 所属测试 ID
     */
    private Long testId;

    /**
     * 题目类型：choice / blank / code
     */
    private String questionType;

    /**
     * 题干
     */
    private String content;

    /**
     * 选择题选项（JSON 数组字符串）
     */
    private String options;

    /**
     * 正确答案（选择/填空/参考代码）
     */
    private String answer;

    /**
     * AI 批改时生成的解析或参考答案
     */
    private String aiAnswer;

    /**
     * 用户作答内容（文本）
     */
    private String userAnswer;

    /**
     * 用户上传图片 URL（编程题拍照）
     */
    private String userUploadUrl;

    /**
     * 本题分值
     */
    private Integer score;

    /**
     * 本题得分（批改后写入）
     */
    private Integer userScore;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}

