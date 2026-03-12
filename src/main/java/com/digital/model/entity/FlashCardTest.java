package com.digital.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 闪卡测试主记录
 *
 * 对应表：flashcard_test
 */
@TableName("flashcard_test")
@Data
public class FlashCardTest implements Serializable {

    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 学习路径 ID（可选，用于关联路径）
     */
    private Long pathId;

    /**
     * 闪卡或学习节点 ID
     */
    private String nodeId;

    /**
     * 难度：easy / medium / hard
     */
    private String difficulty;

    /**
     * 总分
     */
    private Integer score;

    /**
     * 本次测试亮度 / 进度（0-100），与分数保持一致，便于统计与前端展示
     */
    private Integer litProgress;

    /**
     * 测试时间
     */
    private Date testTime;

    /**
     * AI 学习建议
     */
    private String aiAdvice;

    /**
     * 状态：init / finished / cancelled 等
     */
    private String status;

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

