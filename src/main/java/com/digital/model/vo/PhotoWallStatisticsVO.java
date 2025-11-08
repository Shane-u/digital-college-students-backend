package com.digital.model.vo;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 照片墙统计信息视图对象
 *
 * @author Shane
 */
@Data
public class PhotoWallStatisticsVO implements Serializable {

    /**
     * 照片总数
     */
    private Long imageCount;

    /**
     * 最新记录时间
     */
    private Date latestRecordTime;

    /**
     * 总记录时长（天）
     */
    private Long totalDays;

    private static final long serialVersionUID = 1L;
}

