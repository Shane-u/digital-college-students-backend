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
 * 候选人简历的结构化画像
 */
@TableName(value = "ai_candidate_profile")
@Data
public class CandidateProfile implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 对应的简历 ID
     */
    private Long resumeId;

    /**
     * 关联用户 ID
     */
    private Long userId;

    private String name;

    private String email;

    private String phone;

    /**
     * 工作年限（以年为单位，支持小数）
     */
    private Double yearsOfExperience;

    private String highestDegree;

    private String school;

    private String major;

    /**
     * 技能列表 JSON
     */
    private String skillsJson;

    /**
     * 项目经历 JSON
     */
    private String projectsJson;

    /**
     * 工作经历 JSON
     */
    private String workExperiencesJson;

    /**
     * 其他额外字段 JSON
     */
    private String extraJson;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}

