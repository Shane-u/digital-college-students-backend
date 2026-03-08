package com.digital.model.dto.learningpath;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 学习路径节点 JSON 结构
 * 用于 AI 生成、MySQL 存储、Neo4j 映射
 *
 * @author Shane
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LearningPathNode implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 节点编号
     */
    @JsonProperty("nodeId")
    private String nodeId;

    /**
     * 节点标签
     */
    @JsonProperty("label")
    private String label;

    /**
     * 是否为起始节点
     */
    @JsonProperty("isStart")
    private Boolean isStart;

    /**
     * 父亲节点编号
     */
    @JsonProperty("parentNodeId")
    private String parentNodeId;

    /**
     * 当前节点的名字
     */
    @JsonProperty("name")
    private String name;

    /**
     * 当前节点测试点完成进度：已完成测试点数/总测试点数（如 3/5）
     */
    @JsonProperty("testPointsProgress")
    private String testPointsProgress;

    /**
     * 当前节点是否已经被点亮
     */
    @JsonProperty("isLit")
    private Boolean isLit;

    /**
     * 被点亮的时间（ISO 8601 或毫秒时间戳）
     */
    @JsonProperty("litTime")
    private String litTime;

    /**
     * 当前节点的子节点的点亮进度：已完成子节点数/总子节点数（如 3/5）
     */
    @JsonProperty("childrenProgress")
    private String childrenProgress;

    /**
     * 当前节点创建的时间（ISO 8601 或毫秒时间戳）
     */
    @JsonProperty("createdAt")
    private String createdAt;
}
