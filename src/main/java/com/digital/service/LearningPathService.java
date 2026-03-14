package com.digital.service;

import com.digital.model.dto.learningpath.LearningPathPlanRequest;
import com.digital.model.dto.learningpath.LearningPathSaveRequest;
import com.digital.model.dto.learningpath.LearningPathJson;
import com.digital.model.entity.LearningPath;
import com.digital.model.vo.LearningPathGraphVO;
import com.digital.model.vo.LearningPathFlashcardMatchVO;
import com.digital.model.vo.LearningPathRecommendVO;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

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
     * @param shouldStop 是否需要终止（前端 terminate / 断开连接时为 true）
     * @param onChunk 每收到一块内容时的回调，(delta, finished)，finished 为 true 表示流结束
     */
    void planLearningPathStream(LearningPathPlanRequest request, BooleanSupplier shouldStop, BiConsumer<String, Boolean> onChunk);

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

    /**
     * 获取学习路径图谱（所有节点 + 关系），供前端展示 Neo4j 图
     *
     * @param userId 用户 ID
     * @param pathId 路径 ID
     * @return 图谱 VO，路径不存在或不属于当前用户则返回 null
     */
    LearningPathGraphVO getLearningPathGraph(Long userId, String pathId);

    /**
     * 将 matchFlashcards 的返回结果持久化为“学习路径节点↔闪卡”关联
     */
    void persistFlashcardMatches(Long userId, String pathId, LearningPathFlashcardMatchVO vo);

    /**
     * 根据当前用户传入的知识主题，生成建议向 AI 提问的推荐学习知识点列表（以求知姿态表述，供拿去问别的 AI）
     *
     * @param userId 用户 ID
     * @param topic  知识主题
     * @return 结构化的推荐知识点列表，每条含 title、question
     */
    LearningPathRecommendVO recommendKnowledgeQuestions(Long userId, String topic);

    /**
     * 重命名学习路径的 topic（Saga：先更新 Neo4j，再更新 MySQL）
     *
     * @param userId  用户 ID
     * @param pathId  路径 ID
     * @param newTopic 新的主题名称
     * @return 是否成功
     */
    boolean renameTopic(Long userId, String pathId, String newTopic);
}
