package com.digital.model.vo;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 里程碑统计信息视图对象
 *
 * @author Shane
 */
@Data
public class MilestoneStatisticsVO implements Serializable {

    /**
     * 里程碑总数（4星及以上）
     */
    private Long milestoneCount;

    /**
     * 最新的成长记录时间
     */
    private Date latestRecordTime;

    /**
     * 记录总时长（天）- 当前时间减去最早记录时间
     */
    private Long totalDays;

    private static final long serialVersionUID = 1L;
}

