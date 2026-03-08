package com.digital.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Date;

/**
 * 孪孪伴学 - 学习路径
 * 存储用户的学习路径元数据及 JSON 格式的图谱信息
 *
 * @author Shane
 */
@TableName(value = "learning_path")
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LearningPath {

    /**
     * 学习路径 ID（Saga 模式下需先生成，用于先写 Neo4j 再写 MySQL）
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 路径主题（用于区分同一用户的多套学习路径）
     */
    private String topic;

    /**
     * 路径描述
     */
    private String description;

    /**
     * JSON 格式的学习路径图谱（节点列表及关系）
     * 结构见 LearningPathNode
     */
    private String pathJson;

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
}
