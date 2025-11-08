package com.digital.model.dto.growthrecord;

import com.digital.common.PageRequest;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 查询成长记录请求
 *
 * @author Shane
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GrowthRecordQueryRequest extends PageRequest implements Serializable {

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 事件描述（模糊查询）
     */
    private String eventDesc;

    /**
     * 重要程度（最小值，用于里程碑查询）
     */
    private BigDecimal minImportance;

    private static final long serialVersionUID = 1L;
}

