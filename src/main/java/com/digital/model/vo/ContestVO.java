package com.digital.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 竞赛视图对象
 *
 * @author Shane
 */
@Data
public class ContestVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 竞赛ID
     */
    private Long contestId;

    /**
     * 竞赛名称
     */
    private String contestName;

    /**
     * 竞赛URL
     */
    private String contestUrl;

    /**
     * 是否为考试：0-否，2-是
     */
    private Integer isExam;

    /**
     * 竞赛状态
     */
    private Integer isContestStatus;

    /**
     * 报名开始时间（时间戳）
     */
    private Long registStartTime;

    /**
     * 报名结束时间（时间戳）
     */
    private Long registEndTime;

    /**
     * 竞赛开始时间（时间戳）
     */
    private Long contestStartTime;

    /**
     * 竞赛结束时间（时间戳）
     */
    private Long contestEndTime;

    /**
     * 缩略图URL
     */
    private String thumbPic;

    /**
     * 级别名称（校级/市级/省级等）
     */
    private String levelName;

    /**
     * 主办方
     */
    private String organiser;

    /**
     * 主办方名称
     */
    private String organiserName;

    /**
     * 参赛范围
     */
    private String enterRange;

    /**
     * 竞赛一级分类
     */
    private String contestClassFirst;

    /**
     * 竞赛二级分类
     */
    private String contestClassSecond;

    /**
     * 竞赛二级分类ID
     */
    private Integer contestClassSecondId;

    /**
     * 时间状态
     */
    private Integer timeStatus;

    /**
     * 时间状态名称
     */
    private String timeName;

    /**
     * 排名
     */
    private Integer rank;

    /**
     * 是否新竞赛
     */
    private Integer isNew;

    /**
     * 模块
     */
    private Integer module;
}

