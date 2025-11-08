package com.digital.model.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import lombok.Data;

/**
 * 成长记录视图对象
 *
 * @author Shane
 */
@Data
public class GrowthRecordVO implements Serializable {

    /**
     * 成长记录id
     */
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

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
     * 创建时间
     */
    private Date createTime;

    /**
     * 图片列表
     */
    private List<GrowthImageVO> images;

    /**
     * 文件列表
     */
    private List<GrowthFileVO> files;

    private static final long serialVersionUID = 1L;
}

