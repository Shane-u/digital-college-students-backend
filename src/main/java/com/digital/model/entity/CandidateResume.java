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
 * 候选人原始简历与解析结果
 */
@TableName(value = "ai_candidate_resume")
@Data
public class CandidateResume implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联用户 ID
     */
    private Long userId;

    /**
     * 原始上传文件在对象存储或本地的访问地址
     */
    private String fileUrl;

    /**
     * 原始文件名
     */
    private String originalFilename;

    /**
     * 文件 MIME 类型
     */
    private String fileType;

    /**
     * 解析出的纯文本内容
     */
    private String rawText;

    /**
     * 结构化解析 JSON（如基础信息、教育、经历等）
     */
    private String parsedJson;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 逻辑删除标记
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}

