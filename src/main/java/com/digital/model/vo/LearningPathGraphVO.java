package com.digital.model.vo;

import com.digital.model.dto.learningpath.LearningPathNode;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 学习路径图谱 VO
 * 供前端展示 Neo4j 图谱：节点 + 关系
 *
 * @author Shane
 */
@Data
public class LearningPathGraphVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 路径 ID（图谱根标识，前端可作为根节点 id）
     */
    private String pathId;

    /**
     * 路径主题
     */
    private String topic;

    /**
     * 所有节点
     */
    private List<LearningPathNode> nodes;

    /**
     * 关系：sourceNodeId -> targetNodeId，type 为 HAS_NODE（路径→根）或 PARENT_OF（父→子）
     */
    private List<Relationship> relationships;

    @Data
    public static class Relationship implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 源节点 id（可为 pathId 表示路径根，或 nodeId） */
        private String sourceNodeId;
        /** 目标节点 nodeId */
        private String targetNodeId;
        /** 关系类型：HAS_NODE、PARENT_OF */
        private String type;
    }
}
