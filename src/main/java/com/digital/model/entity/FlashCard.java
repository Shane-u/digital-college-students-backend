package com.digital.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Date;
import lombok.Data;

/**
 * 记忆闪卡
 *
 * @author Shane
 */
@TableName(value = "flash_card")
@Data
@JsonIgnoreProperties(ignoreUnknown = true) // 忽略Redis序列化时未知字段的警告
public class FlashCard {

    /**
     * 闪卡id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 知识点标题
     */
    private String title;

    /**
     * 详细知识点内容（纯文本）
     */
    private String content;

    /**
     * 闪卡HTML内容（包含HTML+CSS+SVG动画）
     */
    private String htmlContent;

    /**
     * 原始AI回答内容（用于生成闪卡的原始内容）
     */
    private String originalContent;

    /**
     * 下次复习时间（遵循艾宾浩斯曲线）
     */
    private Date nextReviewTime;

    /**
     * 复习次数
     */
    private Integer repetition;

    /**
     * 最后复习时间
     */
    private Date lastReviewTime;

    /**
     * 易忘系数（Easiness Factor），SM-2算法核心参数
     * 初始值为2.5
     */
    private Double ef;

    /**
     * 复习间隔（天），SM-2算法核心参数
     * 初次复习为0
     */
    @TableField("`interval`") // 解决MySQL关键字冲突
    private Integer interval;

    /**
     * 难度等级（1-重来，2-困难，3-良好，4-简单）
     */
    private Integer difficultyLevel;

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

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}


