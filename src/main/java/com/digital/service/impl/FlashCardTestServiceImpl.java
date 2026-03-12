package com.digital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.digital.common.ErrorCode;
import com.digital.exception.BusinessException;
import com.digital.manager.DoubaoManager;
import com.digital.mapper.FlashCardTestMapper;
import com.digital.mapper.FlashCardTestQuestionMapper;
import com.digital.model.dto.chat.ChatRequest;
import com.digital.model.dto.chat.ChatResponse;
import com.digital.model.dto.chat.Message;
import com.digital.model.dto.flashcard.FlashCardTestAnswerDTO;
import com.digital.model.dto.flashcard.FlashCardTestGenerateRequest;
import com.digital.model.dto.flashcard.FlashCardTestSubmitRequest;
import com.digital.model.entity.FlashCard;
import com.digital.model.entity.FlashCardTest;
import com.digital.model.entity.FlashCardTestQuestion;
import com.digital.model.entity.GrowthRecord;
import com.digital.model.vo.FlashCardTestQuestionVO;
import com.digital.model.vo.FlashCardTestResultVO;
import com.digital.model.vo.FlashCardTestVO;
import com.digital.service.FlashCardService;
import com.digital.service.FlashCardTestService;
import com.digital.service.GrowthRecordService;
import com.digital.service.Neo4jFlashCardService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 闪卡 AI 测试服务实现
 *
 * 核心职责：
 * 1. 调用豆包生成分级测试题（JSON）
 * 2. 保存测试与题目到 MySQL
 * 3. 根据作答批改打分 & 生成学习建议
 * 4. 点亮 Neo4j 节点并同步成长轨迹
 */
@Service
@Slf4j
public class FlashCardTestServiceImpl implements FlashCardTestService {

    private static final String CACHE_KEY_PREFIX = "flashcard:test:";

    @Resource
    private DoubaoManager doubaoManager;

    @Resource
    private FlashCardService flashCardService;

    @Resource
    private FlashCardTestMapper flashCardTestMapper;

    @Resource
    private FlashCardTestQuestionMapper flashCardTestQuestionMapper;

    @Resource
    private Neo4jFlashCardService neo4jFlashCardService;

    @Resource
    private GrowthRecordService growthRecordService;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public FlashCardTestVO generateTest(Long userId, FlashCardTestGenerateRequest request) {
        if (userId == null || request == null || StringUtils.isBlank(request.getNodeId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        String difficulty = normalizeDifficulty(request.getDifficulty());

        // 1. 获取闪卡内容（这里约定 nodeId 即为 FlashCard 的 id）
        FlashCard flashCard = flashCardService.getById(request.getNodeId());
        if (flashCard == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "闪卡节点不存在");
        }

        // 2. Redis 缓存检查（避免同用户同节点同难度频繁出题）
        String cacheKey = CACHE_KEY_PREFIX + userId + ":" + request.getNodeId() + ":" + difficulty;
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.isNotBlank(cachedJson)) {
            try {
                return objectMapper.readValue(cachedJson, FlashCardTestVO.class);
            } catch (Exception e) {
                log.warn("解析缓存测试题失败，将重新生成: key={}, error={}", cacheKey, e.getMessage());
            }
        }

        // 3. 调用豆包生成测试题
        String prompt = buildGeneratePrompt(flashCard.getContent(), difficulty);
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setStream(false);
        chatRequest.setUserId(userId);
        chatRequest.setMessages(List.of(new Message("user", prompt)));

        ChatResponse response = doubaoManager.chat(chatRequest);
        String content = response != null ? response.getContent() : null;
        if (StringUtils.isBlank(content)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 返回内容为空");
        }

        // 4. 解析 JSON，生成题目实体并落库
        JsonNode root = parseQuestionsJson(content);
        List<FlashCardTestQuestion> questionEntities = buildQuestionEntitiesFromJson(root, difficulty);

        if (questionEntities.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 未生成有效测试题");
        }

        Date now = new Date();
        FlashCardTest test = new FlashCardTest();
        test.setUserId(userId);
        test.setPathId(request.getPathId());
        test.setNodeId(request.getNodeId());
        test.setDifficulty(difficulty);
        test.setStatus("init");
        test.setCreateTime(now);
        test.setUpdateTime(now);

        // 插入主测试记录
        flashCardTestMapper.insert(test);

        // 设置 testId 并插入题目
        for (FlashCardTestQuestion q : questionEntities) {
            q.setTestId(test.getId());
            q.setCreateTime(now);
            q.setUpdateTime(now);
            flashCardTestQuestionMapper.insert(q);
        }

        // 5. 组装返回 VO
        FlashCardTestVO vo = new FlashCardTestVO();
        vo.setTestId(test.getId());
        vo.setNodeId(test.getNodeId());
        vo.setDifficulty(test.getDifficulty());

        List<FlashCardTestQuestionVO> questionVOList = new ArrayList<>();
        for (FlashCardTestQuestion q : questionEntities) {
            FlashCardTestQuestionVO qvo = new FlashCardTestQuestionVO();
            qvo.setId(q.getId());
            qvo.setQuestionType(q.getQuestionType());
            qvo.setContent(q.getContent());
            qvo.setDifficulty(difficulty);
            qvo.setScore(q.getScore());
            if (StringUtils.isNotBlank(q.getOptions())) {
                try {
                    List<String> opts = objectMapper.readValue(q.getOptions(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    qvo.setOptions(opts);
                } catch (Exception e) {
                    log.warn("解析选项失败: questionId={}, error={}", q.getId(), e.getMessage());
                }
            }
            questionVOList.add(qvo);
        }
        vo.setQuestions(questionVOList);

        // 6. 缓存一份（1 小时）
        try {
            redisTemplate.opsForValue()
                    .set(cacheKey, objectMapper.writeValueAsString(vo), 1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("缓存测试题失败: key={}, error={}", cacheKey, e.getMessage());
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlashCardTestResultVO submitAndCorrect(Long userId, FlashCardTestSubmitRequest request) {
        if (userId == null || request == null || request.getTestId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        if (request.getAnswers() == null || request.getAnswers().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "答案列表不能为空");
        }

        FlashCardTest test = flashCardTestMapper.selectById(request.getTestId());
        if (test == null || !userId.equals(test.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作该测试");
        }

        List<FlashCardTestQuestion> questions = flashCardTestQuestionMapper.selectList(
                new QueryWrapper<FlashCardTestQuestion>().eq("testId", test.getId()));
        if (questions == null || questions.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "测试题目不存在");
        }

        // 1. 批改：客观题直接对比，主观题调用豆包
        // 题型分值约束：
        // - 选择题：3 分 * 10 = 30
        // - 填空题：5 分 * 6 = 30
        // - 编程题：10 分 * 4 = 40
        // 理论总分 = 100 分
        int objectiveScore = 0;
        int codeScore = 0;

        List<String> wrongTopics = new ArrayList<>();

        for (FlashCardTestQuestion q : questions) {
            FlashCardTestAnswerDTO answerDTO = findAnswerForQuestion(request.getAnswers(), q.getId());
            if (answerDTO == null) {
                // 未作答视为 0 分，直接跳过（不累加得分）
                continue;
            }

            if ("choice".equals(q.getQuestionType()) || "blank".equals(q.getQuestionType())) {
                int s = safeScore(q.getScore());
                if (StringUtils.equals(StringUtils.trimToEmpty(q.getAnswer()),
                        StringUtils.trimToEmpty(answerDTO.getUserAnswer()))) {
                    objectiveScore += s;
                } else {
                    wrongTopics.add(q.getContent());
                }
                q.setUserAnswer(answerDTO.getUserAnswer());
            } else if ("code".equals(q.getQuestionType())) {
                int s = safeScore(q.getScore());
                // 调用豆包对编程题进行主观评分（0-题目满分）
                int thisCodeScore = callDoubaoForCodeScore(userId, q, answerDTO);
                codeScore += thisCodeScore;
                if (thisCodeScore * 1.0 / s < 0.6) {
                    wrongTopics.add(q.getContent());
                }
                q.setUserAnswer(answerDTO.getUserAnswer());
                q.setUserUploadUrl(answerDTO.getUserUploadUrl());
            }
            q.setUpdateTime(new Date());
            flashCardTestQuestionMapper.updateById(q);
        }

        // 2. 直接按题目分值求和，总分理论上为 100 分
        int totalScore = objectiveScore + codeScore;
        // 安全裁剪到 0-100 范围
        totalScore = Math.max(0, Math.min(totalScore, 100));

        // 3. 调用豆包生成学习建议
        String advicePrompt = buildAdvicePrompt(totalScore, wrongTopics);
        String aiAdvice = "";
        try {
            ChatRequest chatRequest = new ChatRequest();
            chatRequest.setStream(false);
            chatRequest.setUserId(userId);
            chatRequest.setMessages(List.of(new Message("user", advicePrompt)));
            ChatResponse adviceResp = doubaoManager.chat(chatRequest);
            aiAdvice = adviceResp != null ? StringUtils.defaultString(adviceResp.getContent()) : "";
        } catch (Exception e) {
            log.warn("生成学习建议失败: testId={}, error={}", test.getId(), e.getMessage());
        }

        // 4. 更新测试主记录
        Date now = new Date();
        test.setScore(totalScore);
        test.setLitProgress(Math.max(0, Math.min(totalScore, 100)));
        test.setAiAdvice(aiAdvice);
        test.setStatus("finished");
        test.setTestTime(now);
        test.setUpdateTime(now);
        flashCardTestMapper.updateById(test);

        boolean pass = totalScore >= 60;

        // 5. 点亮 Neo4j 节点（写 Neo4j）+ 同步成长轨迹（写 MySQL）
        // 简化版 Saga：如果成长记录写入失败，这里不回滚点亮，只记录日志
        try {
            neo4jFlashCardService.updateFlashCardLitStatus(
                    userId,
                    test.getNodeId(),
                    pass,
                    totalScore,
                    test.getDifficulty(),
                    now.getTime()
            );
            // 自底向上刷新父节点（层级节点 / 根节点）的点亮进度与状态
            neo4jFlashCardService.updateParentLitProgress(userId, test.getNodeId());
        } catch (Exception e) {
            log.error("更新 Neo4j 点亮状态失败: userId={}, nodeId={}, error={}",
                    userId, test.getNodeId(), e.getMessage(), e);
        }

        try {
            syncGrowthRecord(userId, test, totalScore, pass);
        } catch (Exception e) {
            log.error("同步成长轨迹失败: userId={}, testId={}, error={}",
                    userId, test.getId(), e.getMessage(), e);
        }

        return FlashCardTestResultVO.builder()
                .testId(test.getId())
                .totalScore(totalScore)
                .pass(pass)
                .aiAdvice(aiAdvice)
                .litStatus(pass ? 1 : 0)
                .litProgress(Math.max(0, Math.min(totalScore, 100)))
                .build();
    }

    private String normalizeDifficulty(String difficulty) {
        if (StringUtils.isBlank(difficulty)) {
            return "easy";
        }
        String d = difficulty.toLowerCase();
        if (!"easy".equals(d) && !"medium".equals(d) && !"hard".equals(d)) {
            return "easy";
        }
        return d;
    }

    private String buildGeneratePrompt(String nodeContent, String difficulty) {
        String diffLabel;
        switch (difficulty) {
            case "hard":
                diffLabel = "困难";
                break;
            case "medium":
                diffLabel = "中等";
                break;
            default:
                diffLabel = "简单";
        }
        return String.format("""
                请根据以下闪卡学习内容：%s，生成%s难度的测试题，共20题，
                其中：
                - 选择题10道：每题必须提供 4 个选项，且分别以“A、B、C、D”开头，例如 ["A. 选项1","B. 选项2","C. 选项3","D. 选项4"]，并给出正确答案（用选项全文或选项字母均可，但要与 options 对应）；
                - 填空题6道：每道题至少2个填空位，含正确答案；
                - 编程题4道：含题目要求和参考代码。
                输出格式为 JSON，仅返回 JSON，格式如下：
                {
                  "questions": [
                    {
                      "type": "choice/blank/code",
                      "content": "题干",
                      "options": ["A. 选项1","B. 选项2","C. 选项3","D. 选项4"], // 仅选择题，必须为 A/B/C/D 四个选项
                      "blanks": ["填空位1","填空位2"], // 仅填空题
                      "answer": "正确答案/参考代码",
                      "difficulty": "easy/medium/hard"
                    }
                  ]
                }
                """, nodeContent, diffLabel);
    }

    private JsonNode parseQuestionsJson(String content) {
        try {
            String cleaned = content.trim();
            if (cleaned.startsWith("```")) {
                int idx = cleaned.indexOf('\n');
                if (idx > 0) {
                    cleaned = cleaned.substring(idx + 1);
                }
                int endIdx = cleaned.lastIndexOf("```");
                if (endIdx > 0) {
                    cleaned = cleaned.substring(0, endIdx);
                }
            }
            return objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.error("解析测试题 JSON 失败: raw={}", content, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 返回的测试题 JSON 格式错误");
        }
    }

    private List<FlashCardTestQuestion> buildQuestionEntitiesFromJson(JsonNode root, String difficulty) {
        List<FlashCardTestQuestion> list = new ArrayList<>();
        JsonNode questionsNode = root.path("questions");
        if (!questionsNode.isArray()) {
            return list;
        }
        for (JsonNode qn : questionsNode) {
            String type = qn.path("type").asText("");
            String content = qn.path("content").asText("");
            if (StringUtils.isAnyBlank(type, content)) {
                continue;
            }
            FlashCardTestQuestion q = new FlashCardTestQuestion();
            q.setQuestionType(type);
            q.setContent(content);
            q.setAnswer(qn.path("answer").asText(""));

            // 分值简单按题型区分：选择 3 分、填空 4 分、编程 8 分，总体约 100
            if ("choice".equals(type)) {
                // 选择题：每题 3 分，10 题共 30 分
                q.setScore(3);
            } else if ("blank".equals(type)) {
                // 填空题：每题 5 分，6 题共 30 分
                q.setScore(5);
            } else if ("code".equals(type)) {
                // 编程题：每题 10 分，4 题共 40 分
                q.setScore(10);
            } else {
                // 兜底：未知题型按 3 分处理
                q.setScore(3);
            }

            // 选择题选项
            if ("choice".equals(type) && qn.has("options")) {
                try {
                    q.setOptions(objectMapper.writeValueAsString(qn.get("options")));
                } catch (Exception e) {
                    log.warn("序列化选项失败: {}", e.getMessage());
                }
            }

            list.add(q);
        }
        return list;
    }

    private FlashCardTestAnswerDTO findAnswerForQuestion(List<FlashCardTestAnswerDTO> answers, Long questionId) {
        for (FlashCardTestAnswerDTO a : answers) {
            if (a != null && questionId.equals(a.getQuestionId())) {
                return a;
            }
        }
        return null;
    }

    private int safeScore(Integer s) {
        return s == null || s <= 0 ? 1 : s;
    }

    private int callDoubaoForCodeScore(Long userId,
                                       FlashCardTestQuestion question,
                                       FlashCardTestAnswerDTO answerDTO) {
        String userCode = StringUtils.defaultString(answerDTO.getUserAnswer());
        String uploadUrl = answerDTO.getUserUploadUrl();
        // 这里暂不接入 OCR，直接用文本或提示图片 URL
        StringBuilder sb = new StringBuilder();
        sb.append("请作为专业编程老师，按照满分 ").append(safeScore(question.getScore()))
                .append(" 分批改下面的编程题，仅返回一个 JSON：")
                .append("{\"score\": 数字, \"comment\": \"简短评价\"}。")
                .append("\n\n题目：").append(question.getContent())
                .append("\n标准参考代码：").append(StringUtils.defaultString(question.getAnswer()))
                .append("\n\n学生作答：");
        if (StringUtils.isNotBlank(userCode)) {
            sb.append(userCode);
        }
        if (StringUtils.isNotBlank(uploadUrl)) {
            sb.append("\n（如果需要，可根据图片大致推测代码，图片地址：").append(uploadUrl).append("）");
        }

        try {
            ChatRequest chatRequest = new ChatRequest();
            chatRequest.setStream(false);
            chatRequest.setUserId(userId);
            chatRequest.setMessages(List.of(new Message("user", sb.toString())));
            ChatResponse resp = doubaoManager.chat(chatRequest);
            String json = resp != null ? resp.getContent() : null;
            if (StringUtils.isBlank(json)) {
                return 0;
            }
            JsonNode node = parseQuestionsJson(json);
            int score = node.path("score").asInt(0);
            String comment = node.path("comment").asText("");
            question.setAiAnswer(comment);
            return Math.max(0, Math.min(score, safeScore(question.getScore())));
        } catch (Exception e) {
            log.warn("编程题批改失败，按 0 分处理: testQuestionId={}, error={}", question.getId(), e.getMessage());
            return 0;
        }
    }

    private String buildAdvicePrompt(int totalScore, List<String> wrongTopics) {
        String topics = wrongTopics.isEmpty() ? "无明显错题" : String.join("；", wrongTopics);
        return String.format("""
                用户本次闪卡测试总分为 %d 分，错题涉及的知识点有：%s。
                请在 100 字以内，给出一段中文学习建议，语气友好、具体，可结合“需要重点复习的知识点”和“建议如何使用闪卡或学习路径继续巩固”两个方面。
                """, totalScore, topics);
    }

    /**
     * 将测试结果同步写入成长记录模块（简化：新增一条成长记录）
     */
    private void syncGrowthRecord(Long userId, FlashCardTest test, int totalScore, boolean pass) {
        GrowthRecord record = new GrowthRecord();
        record.setUserId(userId);
        String desc = String.format("完成闪卡节点 %s 的 %s 难度测试，得分 %d 分（%s）",
                test.getNodeId(),
                test.getDifficulty(),
                totalScore,
                pass ? "已点亮" : "未点亮");
        record.setEventDesc(desc);
        record.setRecordTime(new Date());
        record.setCreateTime(new Date());
        growthRecordService.save(record);
    }
}

