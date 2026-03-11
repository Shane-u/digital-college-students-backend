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
 * AI 面试中产生的题目
 */
@TableName(value = "ai_interview_question")
@Data
public class InterviewQuestion implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    /**
     * 题目在当前会话中的序号（从 1 开始）
     */
    private Integer orderNo;

    /**
     * 题目类型：BEHAVIORAL/TECHNICAL/CODING 等
     */
    private String type;

    /**
     * 题目文本内容
     */
    private String content;

    /**
     * 若为编程题，可关联题目 ID 或保存题目 JSON
     */
    private String codingTaskId;

    /**
     * 扩展字段 JSON（样例输入输出、考察点等）
     */
    private String metadataJson;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}

