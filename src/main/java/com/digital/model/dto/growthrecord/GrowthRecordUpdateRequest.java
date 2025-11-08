package com.digital.model.dto.growthrecord;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import lombok.Data;

/**
 * 更新成长记录请求
 *
 * @author Shane
 */
@Data
public class GrowthRecordUpdateRequest implements Serializable {

    /**
     * 成长记录id
     */
    private Long id;

    /**
     * 事件描述
     */
    private String eventDesc;

    /**
     * 个人感悟
     */
    private String reflection;

    /**
     * 重要程度（最多五颗星，支持4.5颗星）
     */
    private BigDecimal importance;

    /**
     * 记录的时间
     */
    private Date recordTime;

    /**
     * 图片ID列表
     */
    private List<Long> imageIds;

    /**
     * 文件ID列表
     */
    private List<Long> fileIds;

    private static final long serialVersionUID = 1L;
}

