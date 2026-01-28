package com.digital.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 推荐结果实体
 *
 * @author Shane
 */
@TableName(value = "recommendation_result")
@Data
public class RecommendationResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 物品类型：CONTEST(竞赛)/JOB(职业)
     */
    private String itemType;

    /**
     * 物品id
     */
    private Long itemId;

    /**
     * 推荐分数
     */
    private BigDecimal score;

    /**
     * 推荐算法：CONTENT_BASED(内容推荐)/COLLABORATIVE_FILTERING(协同过滤)/HYBRID(混合推荐)
     */
    private String algorithm;

    /**
     * 推荐理由
     */
    private String reason;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 过期时间（推荐结果有效期）
     */
    private Date expireTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    /**
     * 推荐算法枚举
     */
    public enum Algorithm {
        CONTENT_BASED("CONTENT_BASED", "内容推荐"),
        COLLABORATIVE_FILTERING("COLLABORATIVE_FILTERING", "协同过滤"),
        HYBRID("HYBRID", "混合推荐"),
        DUAL_TOWER("DUAL_TOWER", "双塔模型推荐"),
        FALLBACK("FALLBACK", "降级推荐");

        private final String code;
        private final String desc;

        Algorithm(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public String getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }
    }
}
