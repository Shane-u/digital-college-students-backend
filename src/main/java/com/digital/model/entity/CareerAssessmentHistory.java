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
 * 职业测评历史（心理测评等）
 */
@TableName(value = "career_assessment_history")
@Data
public class CareerAssessmentHistory implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /**
     * 测评类型
     */
    private String assessmentType;

    /**
     * 来源（如：web/app/import）
     */
    private String source;

    /**
     * 测评结果 JSON（字符串）
     */
    private String assessmentJson;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}

