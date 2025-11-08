package com.digital.model.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 成长记录事件视图对象
 *
 * @author Shane
 */
@Data
public class GrowthRecordEventVO implements Serializable {

    /**
     * 成长记录id
     */
    private Long id;

    /**
     * 事件描述
     */
    private String eventDesc;

    private static final long serialVersionUID = 1L;
}

