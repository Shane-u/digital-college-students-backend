package com.digital.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * AI 面试中用户的回答
 */
@TableName(value = "ai_interview_answer")
@Data
public class InterviewAnswer implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    private Long questionId;

    private Long userId;

    /**
     * 识别后的文本内容
     */
    private String textAnswer;

    /**
     * 原始音频地址
     */
    private String audioUrl;

    /**
     * 回答时长（秒）
     */
    private Integer durationSeconds;

    /**
     * ASR 置信度（0-1）
     */
    private Double asrConfidence;

    /**
     * 评分与即时反馈 JSON
     */
    private String evaluationJson;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}

