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
 * 基于简历的分析报告（优势、劣势、建议等）
 */
@TableName(value = "ai_resume_analysis")
@Data
public class ResumeAnalysis implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long resumeId;

    private Long userId;

    private String targetRole;

    private String targetLevel;

    /**
     * 分析结果 JSON
     */
    private String analysisJson;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}

