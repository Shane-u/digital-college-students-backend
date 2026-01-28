package com.digital.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户画像实体
 *
 * @author Shane
 */
@TableName(value = "user_profile")
@Data
public class UserProfile implements Serializable {

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
     * 画像类型：CONTEST(竞赛偏好)/JOB(职业偏好)
     */
    private String profileType;

    /**
     * 特征向量（JSON格式，存储用户偏好特征）
     */
    private String featureVector;

    /**
     * 偏好标签（逗号分隔，如：算法竞赛,机器学习,Python）
     */
    private String preferenceTags;

    /**
     * 最后更新时间
     */
    private Date lastUpdateTime;

    /**
     * 创建时间
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
     * 画像类型枚举
     */
    public enum ProfileType {
        CONTEST("CONTEST", "竞赛偏好"),
        JOB("JOB", "职业偏好");

        private final String code;
        private final String desc;

        ProfileType(String code, String desc) {
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
