package com.digital.service.impl;

import com.digital.exception.BusinessException;
import com.digital.common.ErrorCode;
import com.digital.exception.ThrowUtils;
import com.digital.manager.DoubaoManager;
import com.digital.manager.SiliconFlowManager;
import com.digital.model.dto.chat.ChatRequest;
import com.digital.model.dto.chat.ChatResponse;
import com.digital.model.dto.chat.Message;
import com.digital.model.dto.learningpath.LearningPathJson;
import com.digital.model.dto.learningpath.LearningPathPlanRequest;
import com.digital.model.dto.learningpath.LearningPathSaveRequest;
import com.digital.model.entity.LearningPath;
import com.digital.model.vo.LearningPathGraphVO;
import com.digital.model.vo.LearningPathFlashcardMatchVO;
import com.digital.model.vo.LearningPathRecommendVO;
import com.digital.mapper.LearningPathMapper;
import com.digital.mapper.LearningPathFlashcardMatchMapper;
import com.digital.service.LearningPathNeo4jService;
import com.digital.service.LearningPathService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

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
    private DoubaoManager doubaoManager;

    @Resource
    private LearningPathMapper learningPathMapper;

    @Resource
    private LearningPathNeo4jService learningPathNeo4jService;

    @Resource
    private LearningPathFlashcardMatchMapper learningPathFlashcardMatchMapper;

    @Value("${silicon-flow.learning-path-model:Qwen/Qwen3-30B-A3B-Instruct-2507}")
    private String learningPathModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 兼容流式规划输出：前端可能会把 meta 的 {"sessionId": "..."} 与真正的 {"nodes":[...]} 拼在一起。
     * 这里从原始字符串中提取包含 "nodes" 的那段 JSON 对象。
     */
    private String extractNodesJson(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return s;
        }
        // 如果本身就是合法 JSON 且包含 nodes，直接返回
        try {
            var tree = objectMapper.readTree(s);
            if (tree != null && tree.has("nodes")) {
                return s;
            }
        } catch (Exception ignored) {
        }

        int nodesIdx = s.indexOf("\"nodes\"");
        if (nodesIdx < 0) {
            return s;
        }
        int start = s.lastIndexOf('{', nodesIdx);
        if (start < 0) {
            start = 0;
        }

        // 通过括号计数找到对应的 JSON 结束位置
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return s.substring(start, i + 1).trim();
                }
            }
        }
        return s.substring(start).trim();
    }

    @Override
    public void planLearningPathStream(LearningPathPlanRequest request,
                                       BooleanSupplier shouldStop,
                                       BiConsumer<String, Boolean> onChunk) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }
        String userPrompt = request.getUserPrompt();
        ThrowUtils.throwIf(StringUtils.isBlank(userPrompt), ErrorCode.PARAMS_ERROR, "用户提示词不能为空");

        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(SYSTEM_PROMPT));

        String userContent = userPrompt;
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
        }, shouldStop);
    }

    @Override
    public LearningPath saveLearningPath(LearningPathSaveRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }
        String pathJsonStr = extractNodesJson(request.getPathJson());
        String topic = request.getTopic();
        Long uid = request.getUserId();
        ThrowUtils.throwIf(StringUtils.isBlank(pathJsonStr), ErrorCode.PARAMS_ERROR, "学习路径 JSON 不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(topic), ErrorCode.PARAMS_ERROR, "路径主题不能为空");
        ThrowUtils.throwIf(uid == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");

        LearningPathJson pathJson;
        try {
            pathJson = objectMapper.readValue(pathJsonStr, LearningPathJson.class);
        } catch (Exception e) {
            log.error("解析学习路径 JSON 失败：{}", e.getMessage());
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "学习路径 JSON 格式无效");
        }

        ThrowUtils.throwIf(pathJson == null || pathJson.getNodes() == null || pathJson.getNodes().isEmpty(),
                ErrorCode.PARAMS_ERROR, "学习路径节点不能为空");

        String pathId = UUID.randomUUID().toString();
        LearningPath entity = new LearningPath();
        entity.setId(pathId);
        entity.setUserId(uid);
        entity.setTopic(topic);
        entity.setDescription(request.getDescription());
        entity.setPathJson(pathJsonStr);
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());

        // Saga：先写 Neo4j，再写 MySQL
        try {
            learningPathNeo4jService.saveLearningPath(uid, pathId, topic, pathJson);
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
        if (path == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "学习路径不存在");
        }
        String topic = path.getTopic();

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
            learningPathNeo4jService.updateLearningPath(userId, pathId, topic, pathJson);
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
                learningPathNeo4jService.updateLearningPath(userId, pathId, topic, oldPathJson);
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

    @Override
    public void persistFlashcardMatches(Long userId, String pathId, LearningPathFlashcardMatchVO vo) {
        if (userId == null || StringUtils.isBlank(pathId) || vo == null || StringUtils.isBlank(vo.getClickedNodeId())) {
            return;
        }
        String nodeId = vo.getClickedNodeId();
        // 幂等：先把该 node 旧关联软删除，再插入新关联
        try {
            var oldList = learningPathFlashcardMatchMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.digital.model.entity.LearningPathFlashcardMatch>()
                            .eq("userId", userId)
                            .eq("pathId", pathId)
                            .eq("nodeId", nodeId)
                            .eq("isDelete", 0)
            );
            if (oldList != null) {
                for (var m : oldList) {
                    m.setIsDelete(1);
                    m.setUpdateTime(new Date());
                    learningPathFlashcardMatchMapper.updateById(m);
                }
            }
        } catch (Exception e) {
            log.warn("清理旧 match 关联失败: userId={}, pathId={}, nodeId={}, error={}",
                    userId, pathId, nodeId, e.getMessage());
        }

        List<String> ids = vo.getMatchedFlashcardIds() == null ? List.of() : vo.getMatchedFlashcardIds();
        if (ids.isEmpty()) {
            return;
        }
        Set<String> dedup = new HashSet<>(ids);
        Date now = new Date();
        for (String fid : dedup) {
            if (StringUtils.isBlank(fid)) {
                continue;
            }
            try {
                com.digital.model.entity.LearningPathFlashcardMatch m = new com.digital.model.entity.LearningPathFlashcardMatch();
                m.setUserId(userId);
                m.setPathId(pathId);
                m.setNodeId(nodeId);
                m.setFlashcardId(fid);
                Double score = vo.getScoreMap() == null ? null : vo.getScoreMap().get(fid);
                m.setScore(score);
                m.setCreateTime(now);
                m.setUpdateTime(now);
                m.setIsDelete(0);
                learningPathFlashcardMatchMapper.insert(m);
            } catch (Exception e) {
                log.warn("插入 match 关联失败: userId={}, pathId={}, nodeId={}, flashcardId={}, error={}",
                        userId, pathId, nodeId, fid, e.getMessage());
            }
        }
    }

    @Override
    public LearningPathGraphVO getLearningPathGraph(Long userId, String pathId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(pathId), ErrorCode.PARAMS_ERROR, "路径 ID 不能为空");

        LearningPath path = getById(userId, pathId);
        if (path == null) {
            return null;
        }
        return learningPathNeo4jService.getLearningPathGraph(userId, pathId);
    }

    @Override
    public boolean renameTopic(Long userId, String pathId, String newTopic) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(pathId), ErrorCode.PARAMS_ERROR, "路径 ID 不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(newTopic), ErrorCode.PARAMS_ERROR, "新主题不能为空");

        LearningPath path = getById(userId, pathId);
        if (path == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "学习路径不存在");
        }
        String trimmedTopic = newTopic.trim();
        if (trimmedTopic.equals(path.getTopic())) {
            return true;
        }

        LearningPathJson pathJson;
        try {
            pathJson = objectMapper.readValue(path.getPathJson(), LearningPathJson.class);
        } catch (Exception e) {
            log.error("解析学习路径 JSON 失败（重命名 topic）: pathId={}, error={}", pathId, e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "学习路径数据异常，无法重命名主题");
        }

        String oldTopic = path.getTopic();

        // Saga：先更新 Neo4j（带新 topic），再更新 MySQL；MySQL 失败时回滚 Neo4j 主题
        try {
            learningPathNeo4jService.updateLearningPath(userId, pathId, trimmedTopic, pathJson);
        } catch (Exception e) {
            log.error("Neo4j 重命名学习路径主题失败: pathId={}, newTopic={}, error={}", pathId, trimmedTopic, e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "重命名学习路径主题失败");
        }

        path.setTopic(trimmedTopic);
        path.setUpdateTime(new Date());
        try {
            int rows = learningPathMapper.updateById(path);
            if (rows <= 0) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新 MySQL 失败");
            }
            return true;
        } catch (Exception e) {
            log.error("MySQL 重命名学习路径主题失败，回滚 Neo4j 主题: pathId={}, error={}", pathId, e.getMessage());
            try {
                learningPathNeo4jService.updateLearningPath(userId, pathId, oldTopic, pathJson);
            } catch (Exception ignored) {
                log.error("回滚 Neo4j 主题失败: pathId={}, oldTopic={}", pathId, oldTopic);
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "重命名学习路径主题失败：" + e.getMessage());
        }
    }

    private static final String RECOMMEND_SYSTEM_PROMPT = """
            你是一个学习路径助手。根据用户给出的「知识主题」，生成一份「建议向 AI 提问的推荐学习知识点列表」。
            这些知识点会由用户拿去问别的 AI（如问答机器人），因此每条都要以「求知的姿态」表述：即像学习者向老师/AI 提问那样，写成具体、可回答的问题或学习点。
            要求：
            1. 只输出一个合法的 JSON，不要输出 markdown 代码块、解释或其它文字。
            2. JSON 格式必须为：{"items":[{"title":"知识点标题或分类","question":"建议向 AI 提问的问题（求知口吻）"}, ...]}
            3. items 数量建议 5～15 条，覆盖该主题下从基础到进阶的典型问题。
            4. question 字段必须是完整的、可直接拿去问 AI 的句子，例如：「什么是 XXX？请用简单例子说明」「如何实现 XXX？步骤是什么」。
            """;

    @Override
    public LearningPathRecommendVO recommendKnowledgeQuestions(Long userId, String topic) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(topic), ErrorCode.PARAMS_ERROR, "知识主题不能为空");

        LearningPathRecommendVO vo = new LearningPathRecommendVO();
        vo.setItems(new ArrayList<>());

        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setStream(false);
        chatRequest.setUserId(userId);
        chatRequest.setMessages(List.of(
                Message.system(RECOMMEND_SYSTEM_PROMPT),
                Message.user("请根据以下知识主题，生成建议向 AI 提问的推荐学习知识点列表（JSON 格式）：\n\n主题：" + topic.trim())
        ));

        try {
            ChatResponse resp = doubaoManager.chat(chatRequest);
            String content = resp != null ? resp.getContent() : null;
            if (StringUtils.isBlank(content)) {
                return vo;
            }
            String jsonStr = extractRecommendJson(content);
            if (jsonStr == null) {
                return vo;
            }
            JsonNode root = objectMapper.readTree(jsonStr);
            JsonNode itemsNode = root.path("items");
            if (!itemsNode.isArray()) {
                return vo;
            }
            List<LearningPathRecommendVO.RecommendItem> items = new ArrayList<>();
            for (JsonNode item : itemsNode) {
                LearningPathRecommendVO.RecommendItem ri = new LearningPathRecommendVO.RecommendItem();
                ri.setTitle(item.path("title").asText(""));
                ri.setQuestion(item.path("question").asText(""));
                items.add(ri);
            }
            vo.setItems(items);
        } catch (Exception e) {
            log.warn("推荐知识点生成失败: userId={}, topic={}, error={}", userId, topic, e.getMessage());
        }
        return vo;
    }

    /**
     * 从 AI 返回文本中提取推荐列表的 JSON（去除 ```json 等包裹）
     */
    private String extractRecommendJson(String content) {
        if (content == null) {
            return null;
        }
        String s = content.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.startsWith("```")) {
            int first = s.indexOf('\n');
            if (first > 0) {
                int end = s.indexOf("```", first);
                if (end > first) {
                    s = s.substring(first, end).trim();
                } else {
                    s = s.substring(first).trim();
                }
            } else {
                s = s.replaceFirst("^```\\w*", "").replaceAll("```\\s*$", "").trim();
            }
        }
        try {
            objectMapper.readTree(s);
            return s;
        } catch (Exception e) {
            return null;
        }
    }
}
