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
 * 用户行为记录实体
 *
 * @author Shane
 */
@TableName(value = "user_behavior")
@Data
public class UserBehavior implements Serializable {

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
     * 物品id（竞赛id或职业id）
     */
    private Long itemId;

    /**
     * 行为类型：VIEW(浏览)/CLICK(点击)/COLLECT(收藏)/APPLY(报名/申请)/SHARE(分享)
     */
    private String behaviorType;

    /**
     * 行为权重值（用于计算推荐分数）
     */
    private BigDecimal behaviorValue;

    /**
     * 行为上下文（如来源页面、搜索关键词等）
     */
    private String context;

    /**
     * 行为发生时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    /**
     * 行为类型枚举
     */
    public enum BehaviorType {
        VIEW("VIEW", "浏览", 1.0),
        CLICK("CLICK", "点击", 2.0),
        COLLECT("COLLECT", "收藏", 5.0),
        APPLY("APPLY", "报名/申请", 10.0),
        SHARE("SHARE", "分享", 3.0);

        private final String code;
        private final String desc;
        private final Double weight;

        BehaviorType(String code, String desc, Double weight) {
            this.code = code;
            this.desc = desc;
            this.weight = weight;
        }

        public String getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }

        public Double getWeight() {
            return weight;
        }
    }

    /**
     * 物品类型枚举
     */
    public enum ItemType {
        CONTEST("CONTEST", "竞赛"),
        JOB("JOB", "职业");

        private final String code;
        private final String desc;

        ItemType(String code, String desc) {
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
