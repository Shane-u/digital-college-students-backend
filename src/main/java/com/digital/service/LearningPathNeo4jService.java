package com.digital.service;

import com.digital.model.dto.learningpath.LearningPathJson;
import com.digital.model.dto.learningpath.LearningPathNode;

import java.util.List;

/**
 * 孪孪伴学 - 学习路径 Neo4j 服务
 * 负责学习路径图谱的 CRUD，使用标签+属性（userId、pathId、topic）区分不同用户和路径
 *
 * @author Shane
 */
public interface LearningPathNeo4jService {

    /**
     * 保存学习路径到 Neo4j
     * 每个 JSON 节点 → Neo4j 节点，父子关系 → PARENT_OF
     *
     * @param userId  用户 ID
     * @param pathId  路径 ID
     * @param topic   路径主题
     * @param pathJson 学习路径 JSON
     */
    void saveLearningPath(Long userId, String pathId, String topic, LearningPathJson pathJson);

    /**
     * 更新学习路径（先删除旧图，再写入新图）
     *
     * @param userId  用户 ID
     * @param pathId  路径 ID
     * @param topic   路径主题
     * @param pathJson 学习路径 JSON
     */
    void updateLearningPath(Long userId, String pathId, String topic, LearningPathJson pathJson);

    /**
     * 删除学习路径
     *
     * @param userId 用户 ID
     * @param pathId 路径 ID
     */
    void deleteLearningPath(Long userId, String pathId);

    /**
     * 从 Neo4j 读取学习路径，转换为 JSON 格式
     *
     * @param userId 用户 ID
     * @param pathId 路径 ID
     * @return 学习路径 JSON，不存在则返回 null
     */
    LearningPathJson getLearningPath(Long userId, String pathId);

    /**
     * 获取用户所有学习路径的 pathId 列表
     *
     * @param userId 用户 ID
     * @return pathId 列表
     */
    List<String> listPathIds(Long userId);
}
