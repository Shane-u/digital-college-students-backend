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
 * 职业规划报告历史（Dify 工作流最终报告落库）
 */
@TableName(value = "career_plan_report")
@Data
public class CareerPlanReport implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /**
     * 工作流运行 ID（UUID）
     */
    private String runId;

    /**
     * 最终报告（Markdown）
     */
    private String reportMarkdown;

    /**
     * 失败原因
     */
    private String error;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}

