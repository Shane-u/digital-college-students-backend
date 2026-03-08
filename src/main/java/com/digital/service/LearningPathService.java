package com.digital.service;

import com.digital.model.dto.learningpath.LearningPathPlanRequest;
import com.digital.model.dto.learningpath.LearningPathSaveRequest;
import com.digital.model.dto.learningpath.LearningPathJson;
import com.digital.model.entity.LearningPath;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * 孪孪伴学 - 学习路径服务
 * 负责 AI 规划、保存、CRUD
 *
 * @author Shane
 */
public interface LearningPathService {

    /**
     * 流式规划学习路径
     * 入参：用户提示词、当前学习路径（可为空）
     * 输出：通过 onChunk 回调逐块推送 JSON 内容
     *
     * @param request 规划请求
     * @param onChunk 每收到一块内容时的回调，(delta, finished)，finished 为 true 表示流结束
     */
    void planLearningPathStream(LearningPathPlanRequest request, BiConsumer<String, Boolean> onChunk);

    /**
     * 保存学习路径（Saga：先 Neo4j，再 MySQL）
     *
     * @param request 保存请求
     * @return 保存后的学习路径
     */
    LearningPath saveLearningPath(LearningPathSaveRequest request);

    /**
     * 获取用户的学习路径列表
     *
     * @param userId 用户 ID
     * @return 学习路径列表
     */
    List<LearningPath> listByUserId(Long userId);

    /**
     * 根据 ID 获取学习路径（含 JSON）
     *
     * @param userId 用户 ID
     * @param pathId 路径 ID
     * @return 学习路径，不存在返回 null
     */
    LearningPath getById(Long userId, String pathId);

    /**
     * 从 MySQL 获取学习路径的 JSON
     *
     * @param userId 用户 ID
     * @param pathId 路径 ID
     * @return 学习路径 JSON，不存在返回 null
     */
    LearningPathJson getPathJson(Long userId, String pathId);

    /**
     * 更新学习路径（Saga：先 Neo4j，再 MySQL）
     *
     * @param userId  用户 ID
     * @param pathId  路径 ID
     * @param pathJson 新的 JSON
     * @return 是否成功
     */
    boolean updatePathJson(Long userId, String pathId, LearningPathJson pathJson);

    /**
     * 删除学习路径（Saga：先 Neo4j，再 MySQL）
     *
     * @param userId 用户 ID
     * @param pathId 路径 ID
     * @return 是否成功
     */
    boolean deleteLearningPath(Long userId, String pathId);
}
