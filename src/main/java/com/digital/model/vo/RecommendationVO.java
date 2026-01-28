package com.digital.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 推荐结果VO
 *
 * @author Shane
 */
@Data
public class RecommendationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 物品id（竞赛id或职业id）
     */
    private Long itemId;

    /**
     * 物品名称
     */
    private String itemName;

    /**
     * 推荐分数
     */
    private BigDecimal score;

    /**
     * 推荐理由
     */
    private String reason;

    /**
     * 推荐算法
     */
    private String algorithm;

    /**
     * 物品详情（竞赛VO或职业VO）
     */
    private Object itemDetail;
}
