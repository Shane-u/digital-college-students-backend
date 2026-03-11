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
 * AI 面试会话
 */
@TableName(value = "ai_interview_session")
@Data
public class AiInterviewSession implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long resumeId;

    /**
     * 会话类型：BEHAVIORAL/TECHNICAL/CODING/MIXED
     */
    private String type;

    /**
     * 语言：zh-CN/en-US 等
     */
    private String language;

    /**
     * 难度：JUNIOR/MID/SENIOR 等
     */
    private String difficulty;

    /**
     * 面试官人格/风格标识
     */
    private String persona;

    /**
     * 配置 JSON（是否开启编程题、实时提示等）
     */
    private String configJson;

    /**
     * 当前状态：CREATED/RUNNING/FINISHED/CANCELLED
     */
    private String status;

    /**
     * 实际开始时间
     */
    private Date startedAt;

    /**
     * 实际结束时间
     */
    private Date endedAt;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}

