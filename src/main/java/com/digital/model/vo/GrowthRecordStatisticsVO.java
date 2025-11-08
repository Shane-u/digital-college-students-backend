package com.digital.model.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 成长记录统计信息视图对象
 *
 * @author Shane
 */
@Data
public class GrowthRecordStatisticsVO implements Serializable {

    /**
     * 记录总数
     */
    private Long recordCount;

    /**
     * 照片总数
     */
    private Long imageCount;

    /**
     * 文件总数
     */
    private Long fileCount;

    private static final long serialVersionUID = 1L;
}

