package com.digital.service.impl;

import com.digital.exception.BusinessException;
import com.digital.common.ErrorCode;
import com.digital.exception.ThrowUtils;
import com.digital.manager.SiliconFlowManager;
import com.digital.model.dto.chat.ChatRequest;
import com.digital.model.dto.chat.Message;
import com.digital.model.dto.learningpath.LearningPathJson;
import com.digital.model.dto.learningpath.LearningPathPlanRequest;
import com.digital.model.dto.learningpath.LearningPathSaveRequest;
import com.digital.model.entity.LearningPath;
import com.digital.mapper.LearningPathMapper;
import com.digital.service.LearningPathNeo4jService;
import com.digital.service.LearningPathService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * 孪孪伴学 - 学习路径服务实现
 * 使用 Silicon Flow（Qwen 模型）进行 AI 规划
 *
 * @author Shane
 */
@Service
@Slf4j
public class LearningPathServiceImpl implements LearningPathService {

    private static final String SYSTEM_PROMPT = """
            请根据用户所需要掌握的内容，生成结构化的 JSON 格式的数据，不要输出其他的内容。
            格式要求如下（由于 JSON 格式需要被解析并且放到 neo4j 数据库中，所以要保留好信息）：
            
            输出格式：
            {
              "nodes": [
                {
                  "nodeId": "节点编号，如 1、2、3",
                  "label": "节点标签",
                  "isStart": true/false,
                  "parentNodeId": "父亲节点编号，起始节点为 null",
                  "name": "当前节点的名字",
                  "testPointsProgress": "计划阶段固定为 0/0（后续生成题目后再补充）",
                  "isLit": false,
                  "litTime": null,
                  "childrenProgress": "计划阶段固定为 0/0（后续生成子节点点亮进度后再补充）",
                  "createdAt": "ISO8601 时间或时间戳"
                }
              ]
            }
            
            要求：
            1. 只输出上述 JSON，不要输出任何 markdown 代码块、解释或其他文字。
            2. 每个节点必须包含 nodeId、label、isStart、parentNodeId、name、testPointsProgress、isLit、litTime、childrenProgress、createdAt。
            3. 起始节点（根节点）isStart 为 true，parentNodeId 为 null，name 取为主题名（如用户说「学习 Java」则根节点 name 为 \"Java\" 或 \"Java学习路径\"）。
            4. 子节点的 parentNodeId 必须且只能指向其直接父节点的 nodeId，禁止跨层：根节点只连次级节点，次级节点只连自己的子节点，以此类推，形成严格树形结构。
            5. 计划阶段：所有节点的 testPointsProgress 和 childrenProgress 一律输出 \"0/0\"（不要输出 0/4、0/5 等）。
            6. 复杂度要求：不要只做两层。请输出 3-4 层的学习路径树（根节点 → 模块 → 子模块 → 关键知识点/练习主题），内容要更完整、更细。
               - 节点数量建议 20-60 个（根据主题复杂度自动调整）。
               - label 用于模块归类（如 HTML/CSS/JavaScript/工程化/框架/性能/安全/测试/部署等），name 用具体学习项名称。
            7. nodeId 必须唯一且可比较（推荐用数字字符串递增：\"1\",\"2\",\"3\"...）。
            8. 若用户要求修改当前学习路径，则基于传入的当前路径进行修改后输出（尽量保持 nodeId 稳定，新增节点再分配新 nodeId）。
            """;

    @Resource
    private SiliconFlowManager siliconFlowManager;

    @Resource
    private LearningPathMapper learningPathMapper;

    @Resource
    private LearningPathNeo4jService learningPathNeo4jService;

    @Value("${silicon-flow.learning-path-model:Qwen/Qwen3-30B-A3B-Instruct-2507}")
    private String learningPathModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void planLearningPathStream(LearningPathPlanRequest request, BiConsumer<String, Boolean> onChunk) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(request.getUserPrompt()), ErrorCode.PARAMS_ERROR, "用户提示词不能为空");

        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(SYSTEM_PROMPT));

        String userContent = request.getUserPrompt();
        if (StringUtils.isNotBlank(request.getCurrentPathJson())) {
            userContent += "\n\n当前学习路径（请在此基础上修改）：\n" + request.getCurrentPathJson();
        }
        messages.add(Message.user(userContent));

        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setMessages(messages);
        chatRequest.setModel(learningPathModel);
        chatRequest.setStream(true);
        chatRequest.setUserId(request.getUserId());

        siliconFlowManager.streamChat(chatRequest, streamResponse -> {
            String delta = streamResponse.getDeltaContent();
            if (delta != null && !delta.isEmpty()) {
                onChunk.accept(delta, false);
            }
            if (streamResponse.isFinished()) {
                onChunk.accept("", true);
            }
        });
    }

    @Override
    public LearningPath saveLearningPath(LearningPathSaveRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(request.getPathJson()), ErrorCode.PARAMS_ERROR, "学习路径 JSON 不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(request.getTopic()), ErrorCode.PARAMS_ERROR, "路径主题不能为空");
        ThrowUtils.throwIf(request.getUserId() == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");

        LearningPathJson pathJson;
        try {
            pathJson = objectMapper.readValue(request.getPathJson(), LearningPathJson.class);
        } catch (Exception e) {
            log.error("解析学习路径 JSON 失败：{}", e.getMessage());
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "学习路径 JSON 格式无效");
        }

        ThrowUtils.throwIf(pathJson == null || pathJson.getNodes() == null || pathJson.getNodes().isEmpty(),
                ErrorCode.PARAMS_ERROR, "学习路径节点不能为空");

        String pathId = UUID.randomUUID().toString();
        LearningPath entity = new LearningPath();
        entity.setId(pathId);
        entity.setUserId(request.getUserId());
        entity.setTopic(request.getTopic());
        entity.setDescription(request.getDescription());
        entity.setPathJson(request.getPathJson());
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());

        // Saga：先写 Neo4j，再写 MySQL
        try {
            learningPathNeo4jService.saveLearningPath(
                    entity.getUserId(), pathId, entity.getTopic(), pathJson);
        } catch (Exception e) {
            log.error("保存学习路径到 Neo4j 失败：pathId={}, error={}", pathId, e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存学习路径失败：" + e.getMessage());
        }

        try {
            learningPathMapper.insert(entity);
        } catch (Exception e) {
            log.error("保存学习路径到 MySQL 失败，执行 Neo4j 补偿回滚：pathId={}, error={}", pathId, e.getMessage());
            learningPathNeo4jService.deleteLearningPath(entity.getUserId(), pathId);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存学习路径失败：" + e.getMessage());
        }

        return entity;
    }

    @Override
    public List<LearningPath> listByUserId(Long userId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        return learningPathMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningPath>()
                        .eq(LearningPath::getUserId, userId)
                        .orderByDesc(LearningPath::getCreateTime)
        );
    }

    @Override
    public LearningPath getById(Long userId, String pathId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(pathId), ErrorCode.PARAMS_ERROR, "路径 ID 不能为空");

        LearningPath path = learningPathMapper.selectById(pathId);
        if (path == null || !path.getUserId().equals(userId)) {
            return null;
        }
        return path;
    }

    @Override
    public LearningPathJson getPathJson(Long userId, String pathId) {
        LearningPath path = getById(userId, pathId);
        if (path == null || StringUtils.isBlank(path.getPathJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(path.getPathJson(), LearningPathJson.class);
        } catch (Exception e) {
            log.error("解析学习路径 JSON 失败：pathId={}, error={}", pathId, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean updatePathJson(Long userId, String pathId, LearningPathJson pathJson) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(pathId), ErrorCode.PARAMS_ERROR, "路径 ID 不能为空");
        ThrowUtils.throwIf(pathJson == null || pathJson.getNodes() == null, ErrorCode.PARAMS_ERROR, "学习路径 JSON 不能为空");

        LearningPath path = getById(userId, pathId);
        ThrowUtils.throwIf(path == null, ErrorCode.NOT_FOUND_ERROR, "学习路径不存在");

        LearningPathJson oldPathJson = null;
        try {
            if (StringUtils.isNotBlank(path.getPathJson())) {
                oldPathJson = objectMapper.readValue(path.getPathJson(), LearningPathJson.class);
            }
        } catch (Exception ignored) {
        }

        String jsonStr;
        try {
            jsonStr = objectMapper.writeValueAsString(pathJson);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "学习路径 JSON 序列化失败");
        }

        // Saga：先更新 Neo4j，再更新 MySQL
        try {
            learningPathNeo4jService.updateLearningPath(userId, pathId, path.getTopic(), pathJson);
        } catch (Exception e) {
            log.error("更新 Neo4j 学习路径失败：{}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新学习路径失败");
        }

        path.setPathJson(jsonStr);
        path.setUpdateTime(new Date());
        try {
            int rows = learningPathMapper.updateById(path);
            if (rows <= 0) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新 MySQL 失败");
            }
            return true;
        } catch (Exception e) {
            log.error("更新 MySQL 学习路径失败，执行 Neo4j 补偿回滚：pathId={}, error={}", pathId, e.getMessage());
            if (oldPathJson != null) {
                learningPathNeo4jService.updateLearningPath(userId, pathId, path.getTopic(), oldPathJson);
            } else {
                learningPathNeo4jService.deleteLearningPath(userId, pathId);
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新学习路径失败：" + e.getMessage());
        }
    }

    @Override
    public boolean deleteLearningPath(Long userId, String pathId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(pathId), ErrorCode.PARAMS_ERROR, "路径 ID 不能为空");

        LearningPath path = getById(userId, pathId);
        if (path == null) {
            return false;
        }

        LearningPathJson pathJsonForRollback = null;
        try {
            if (StringUtils.isNotBlank(path.getPathJson())) {
                pathJsonForRollback = objectMapper.readValue(path.getPathJson(), LearningPathJson.class);
            }
        } catch (Exception ignored) {
        }

        // Saga：先删 Neo4j，再删 MySQL；MySQL 失败时恢复 Neo4j
        try {
            learningPathNeo4jService.deleteLearningPath(userId, pathId);
        } catch (Exception e) {
            log.error("从 Neo4j 删除学习路径失败：{}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除学习路径失败");
        }

        try {
            int rows = learningPathMapper.deleteById(pathId);
            if (rows <= 0) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除 MySQL 失败");
            }
            return true;
        } catch (Exception e) {
            log.error("删除 MySQL 学习路径失败，执行 Neo4j 补偿恢复：pathId={}, error={}", pathId, e.getMessage());
            if (pathJsonForRollback != null) {
                learningPathNeo4jService.saveLearningPath(userId, pathId, path.getTopic(), pathJsonForRollback);
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除学习路径失败：" + e.getMessage());
        }
    }
}
