package com.digital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.digital.common.ErrorCode;
import com.digital.exception.BusinessException;
import com.digital.manager.DoubaoManager;
import com.digital.mapper.FlashCardTestAttemptMapper;
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
import com.digital.model.entity.FlashCardTestAttempt;
import com.digital.model.entity.FlashCardTestQuestion;
import com.digital.model.entity.GrowthRecord;
import com.digital.model.vo.FlashCardTestAttemptSummaryVO;
import com.digital.model.vo.FlashCardTestQuestionResultVO;
import com.digital.model.vo.FlashCardTestQuestionVO;
import com.digital.model.vo.FlashCardTestResultVO;
import com.digital.model.vo.FlashCardTestPaperSummaryVO;
import com.digital.model.vo.FlashCardTestVO;
import com.digital.service.FlashCardService;
import com.digital.service.FlashCardTestService;
import com.digital.service.GrowthRecordService;
import com.digital.service.LearningPathLightingService;
import com.digital.service.Neo4jFlashCardService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private FlashCardTestAttemptMapper flashCardTestAttemptMapper;

    @Resource
    private Neo4jFlashCardService neo4jFlashCardService;

    @Resource
    private GrowthRecordService growthRecordService;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private LearningPathLightingService learningPathLightingService;

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
        List<FlashCardTestQuestionResultVO> questionResults = new ArrayList<>();

        for (FlashCardTestQuestion q : questions) {
            FlashCardTestAnswerDTO answerDTO = findAnswerForQuestion(request.getAnswers(), q.getId());
            if (answerDTO == null) {
                // 未作答视为 0 分，直接跳过（不累加得分）
                q.setUserScore(0);
                q.setAiAnswer(StringUtils.defaultIfBlank(q.getAiAnswer(), "未作答，按 0 分计"));
                q.setUpdateTime(new Date());
                flashCardTestQuestionMapper.updateById(q);
                questionResults.add(toQuestionResultVO(q));
                continue;
            }

            String type = q.getQuestionType();
            if ("choice".equals(type)) {
                // 客观选择题：只看选项字母是否匹配
                int s = safeScore(q.getScore());
                String std = StringUtils.trimToEmpty(q.getAnswer());
                String ua = StringUtils.trimToEmpty(answerDTO.getUserAnswer());
                boolean correct = false;
                if (std.length() == 1 && Character.isLetter(std.charAt(0))) {
                    correct = ua.equalsIgnoreCase(std) || ua.startsWith(std + ".") || ua.startsWith(std + " ");
                } else {
                    correct = ua.equals(std);
                }
                if (correct) {
                    objectiveScore += s;
                    q.setUserScore(s);
                } else {
                    wrongTopics.add(q.getContent());
                    q.setUserScore(0);
                }
                q.setUserAnswer(answerDTO.getUserAnswer());
            } else if ("blank".equals(type)) {
                // 填空题：改为调用 AI 进行模糊评分
                int s = safeScore(q.getScore());
                int thisBlankScore = callDoubaoForBlankScore(userId, q, answerDTO);
                objectiveScore += thisBlankScore;
                if (thisBlankScore * 1.0 / s < 0.6) {
                    wrongTopics.add(q.getContent());
                }
                q.setUserAnswer(answerDTO.getUserAnswer());
                q.setUserScore(thisBlankScore);
            } else if ("code".equals(type)) {
                int s = safeScore(q.getScore());
                // 编程题：调用豆包进行主观评分（0-题目满分）
                int thisCodeScore = callDoubaoForCodeScore(userId, q, answerDTO);
                codeScore += thisCodeScore;
                if (thisCodeScore * 1.0 / s < 0.6) {
                    wrongTopics.add(q.getContent());
                }
                q.setUserAnswer(answerDTO.getUserAnswer());
                q.setUserUploadUrl(answerDTO.getUserUploadUrl());
                q.setUserScore(thisCodeScore);
            }
            q.setUpdateTime(new Date());
            flashCardTestQuestionMapper.updateById(q);
            questionResults.add(toQuestionResultVO(q));
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

        // 4. 更新测试主记录（一次测试 = 一个“测试点”实例）
        Date now = new Date();
        // 同一个测试多次作答：只保留历史最高分，低于或等于历史最高分时不回退
        int oldScore = test.getScore() == null ? 0 : test.getScore();
        if (totalScore > oldScore) {
            test.setScore(totalScore);
            test.setLitProgress(Math.max(0, Math.min(totalScore, 100)));
        }
        test.setAiAdvice(aiAdvice);
        test.setStatus("finished");
        test.setTestTime(now);
        test.setUpdateTime(now);
        flashCardTestMapper.updateById(test);

        // 单次测试是否及格（>=60），对应“当前测试点是否点亮”
        boolean pass = totalScore >= 60;

        // 3.1 写入提交历史（可追溯，每次 submit 一条）
        Long submitId = null;
        try {
            FlashCardTestAttempt attempt = new FlashCardTestAttempt();
            attempt.setUserId(userId);
            attempt.setTestId(test.getId());
            attempt.setTotalScore(totalScore);
            attempt.setPass(pass ? 1 : 0);
            attempt.setAiAdvice(aiAdvice);
            attempt.setQuestionResultsJson(objectMapper.writeValueAsString(questionResults));
            attempt.setCreateTime(now);
            attempt.setUpdateTime(now);
            attempt.setIsDelete(0);
            flashCardTestAttemptMapper.insert(attempt);
            submitId = attempt.getId();
        } catch (Exception e) {
            log.error("写入测试提交历史失败: testId={}, error={}", test.getId(), e.getMessage(), e);
        }

        // 4.1 将当前测试作为“测试点节点”写入 Neo4j，便于图谱展示
        try {
            String normalizedDifficulty = normalizeDifficulty(test.getDifficulty());
            neo4jFlashCardService.upsertTestPointNode(
                    userId,
                    test.getNodeId(),
                    test.getId(),
                    normalizedDifficulty,
                    totalScore,
                    pass,
                    now.getTime()
            );
        } catch (Exception e) {
            log.error("写入 Neo4j 测试点节点失败: userId={}, nodeId={}, testId={}, error={}",
                    userId, test.getNodeId(), test.getId(), e.getMessage(), e);
        }

        // 基于所有历史测试点，重新计算当前闪卡的整体点亮状态：
        // litProgress = 已点亮测试点数量 / 总测试点数量 * 100
        // litStatus = litProgress >= 80
        try {
            recomputeFlashCardLitFromTests(userId, test.getNodeId());
        } catch (Exception e) {
            // 按 Saga 思路：这里不回滚主流程，只记录日志，后续可通过定时任务重算
            log.error("根据测试点重算闪卡点亮状态失败: userId={}, nodeId={}, error={}",
                    userId, test.getNodeId(), e.getMessage(), e);
        }

        // 闪卡点亮状态变化后，回溯更新所有受影响的学习路径节点点亮状态
        try {
            learningPathLightingService.recomputeByFlashcard(userId, test.getNodeId());
        } catch (Exception e) {
            log.error("回溯更新学习路径点亮失败: userId={}, flashcardId={}, error={}",
                    userId, test.getNodeId(), e.getMessage(), e);
        }

        try {
            syncGrowthRecord(userId, test, totalScore, pass);
        } catch (Exception e) {
            log.error("同步成长轨迹失败: userId={}, testId={}, error={}",
                    userId, test.getId(), e.getMessage(), e);
        }

        return FlashCardTestResultVO.builder()
                .submitId(submitId)
                .testId(test.getId())
                .totalScore(totalScore)
                .pass(pass)
                .aiAdvice(aiAdvice)
                .litStatus(pass ? 1 : 0)
                .litProgress(Math.max(0, Math.min(totalScore, 100)))
                .questionResults(questionResults)
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

    /**
     * 从包含其他文本/代码块的字符串中，提取最后一个 JSON 对象用于评分。
     * 典型场景：模型返回「```html + 代码块 + ``` + 换行 + {\"score\":..., \"comment\":...}」。
     */
    private JsonNode parseScoreJsonObject(String content) {
        try {
            String cleaned = content.trim();
            // 尝试从字符串末尾向前找到一段 {...}，作为评分 JSON
            int end = cleaned.lastIndexOf('}');
            if (end <= 0) {
                return objectMapper.readTree(cleaned);
            }
            int start = cleaned.lastIndexOf('{', end);
            if (start < 0) {
                return objectMapper.readTree(cleaned);
            }
            String jsonPart = cleaned.substring(start, end + 1).trim();
            return objectMapper.readTree(jsonPart);
        } catch (Exception e) {
            log.error("解析评分 JSON 失败: raw={}", content, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 返回的评分 JSON 格式错误");
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
        if (StringUtils.isBlank(userCode) && StringUtils.isBlank(uploadUrl)) {
            question.setAiAnswer("未作答（文本与图片均为空），按 0 分计");
            return 0;
        }
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
            // 注意：评分场景下，模型可能返回「代码块 + JSON」，这里用专门的解析逻辑只截取最后一个 JSON 对象
            JsonNode node = parseScoreJsonObject(json);
            int score = node.path("score").asInt(0);
            String comment = node.path("comment").asText("");
            question.setAiAnswer(comment);
            return Math.max(0, Math.min(score, safeScore(question.getScore())));
        } catch (Exception e) {
            log.warn("编程题批改失败，按 0 分处理: testQuestionId={}, error={}", question.getId(), e.getMessage());
            return 0;
        }
    }

    /**
     * 调用豆包对填空题进行主观评分（0-题目满分），考虑同义表述、语序差异等。
     */
    private int callDoubaoForBlankScore(Long userId,
                                        FlashCardTestQuestion question,
                                        FlashCardTestAnswerDTO answerDTO) {
        String userAnswer = StringUtils.defaultString(answerDTO.getUserAnswer());
        if (StringUtils.isBlank(userAnswer)) {
            question.setAiAnswer("未作答，按 0 分计");
            return 0;
        }
        int maxScore = safeScore(question.getScore());
        StringBuilder sb = new StringBuilder();
        sb.append("请作为专业前端面试官，按照满分 ").append(maxScore)
                .append(" 分批改下面的填空题，仅返回一个 JSON：")
                .append("{\"score\": 数字, \"comment\": \"简短评价\"}。")
                .append("\n判断时需要考虑同义词、表述顺序差异等情况，尽量宽松一些：")
                .append("\n- 完全正确或高度一致：得满分或接近满分；")
                .append("\n- 部分要点正确：给 50%-80% 的分数；")
                .append("\n- 基本错误或空白：0-30 分。")
                .append("\n\n题目：").append(question.getContent())
                .append("\n标准答案：").append(StringUtils.defaultString(question.getAnswer()))
                .append("\n\n学生作答：").append(userAnswer);

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
            JsonNode node = parseScoreJsonObject(json);
            int score = node.path("score").asInt(0);
            String comment = node.path("comment").asText("");
            question.setAiAnswer(comment);
            return Math.max(0, Math.min(score, maxScore));
        } catch (Exception e) {
            log.warn("填空题批改失败，按 0 分处理: testQuestionId={}, error={}", question.getId(), e.getMessage());
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

    private FlashCardTestQuestionResultVO toQuestionResultVO(FlashCardTestQuestion q) {
        FlashCardTestQuestionResultVO vo = new FlashCardTestQuestionResultVO();
        vo.setId(q.getId());
        vo.setQuestionType(q.getQuestionType());
        vo.setContent(q.getContent());
        vo.setAnswer(q.getAnswer());
        vo.setScore(q.getScore());
        vo.setUserScore(q.getUserScore() == null ? 0 : q.getUserScore());
        vo.setUserAnswer(q.getUserAnswer());
        vo.setUserUploadUrl(q.getUserUploadUrl());
        vo.setAiAnswer(q.getAiAnswer());

        if ("choice".equals(q.getQuestionType()) && StringUtils.isNotBlank(q.getOptions())) {
            try {
                vo.setOptions(objectMapper.readValue(q.getOptions(), new TypeReference<List<String>>() {}));
            } catch (Exception e) {
                vo.setOptions(List.of());
            }
        } else {
            vo.setOptions(List.of());
        }
        return vo;
    }

    @Override
    public List<FlashCardTestPaperSummaryVO> listPapers(Long userId, String nodeId, String difficulty) {
        if (userId == null || StringUtils.isBlank(nodeId)) {
            return List.of();
        }
        QueryWrapper<FlashCardTest> qw = new QueryWrapper<FlashCardTest>()
                .eq("userId", userId)
                .eq("nodeId", nodeId)
                .eq("isDelete", 0);
        if (StringUtils.isNotBlank(difficulty)) {
            qw.eq("difficulty", difficulty);
        }
        qw.orderByDesc("createTime");
        List<FlashCardTest> tests = flashCardTestMapper.selectList(qw);
        if (tests == null || tests.isEmpty()) {
            return List.of();
        }

        List<FlashCardTestPaperSummaryVO> list = new ArrayList<>();
        for (FlashCardTest t : tests) {
            FlashCardTestPaperSummaryVO vo = new FlashCardTestPaperSummaryVO();
            vo.setTestId(t.getId());
            vo.setNodeId(t.getNodeId());
            vo.setDifficulty(t.getDifficulty());

            List<FlashCardTestAttempt> attempts = flashCardTestAttemptMapper.selectList(
                    new QueryWrapper<FlashCardTestAttempt>()
                            .eq("userId", userId)
                            .eq("testId", t.getId())
                            .eq("isDelete", 0)
                            .orderByDesc("createTime"));
            vo.setAttemptCount(attempts == null ? 0 : attempts.size());
            if (attempts != null && !attempts.isEmpty()) {
                FlashCardTestAttempt last = attempts.get(0);
                vo.setLastTotalScore(last.getTotalScore() == null ? 0 : last.getTotalScore());
                vo.setLastSubmitTime(last.getCreateTime());
                int best = attempts.stream()
                        .map(a -> a.getTotalScore() == null ? 0 : a.getTotalScore())
                        .max(Integer::compareTo)
                        .orElse(0);
                vo.setBestTotalScore(best);
            } else {
                vo.setLastTotalScore(0);
                vo.setBestTotalScore(0);
                vo.setLastSubmitTime(null);
            }
            list.add(vo);
        }
        return list;
    }

    @Override
    public List<FlashCardTestAttemptSummaryVO> listAttempts(Long userId, Long testId) {
        if (userId == null || testId == null) {
            return List.of();
        }
        List<FlashCardTestAttempt> attempts = flashCardTestAttemptMapper.selectList(
                new QueryWrapper<FlashCardTestAttempt>()
                        .eq("userId", userId)
                        .eq("testId", testId)
                        .eq("isDelete", 0)
                        .orderByDesc("createTime"));
        if (attempts == null || attempts.isEmpty()) {
            return List.of();
        }
        List<FlashCardTestAttemptSummaryVO> list = new ArrayList<>();
        for (FlashCardTestAttempt a : attempts) {
            FlashCardTestAttemptSummaryVO vo = new FlashCardTestAttemptSummaryVO();
            vo.setAttemptId(a.getId());
            vo.setTestId(a.getTestId());
            vo.setTotalScore(a.getTotalScore() == null ? 0 : a.getTotalScore());
            vo.setPass(a.getPass() != null && a.getPass() == 1);
            vo.setSubmitTime(a.getCreateTime());
            list.add(vo);
        }
        return list;
    }

    @Override
    public List<FlashCardTestQuestionResultVO> getPaperQuestionsWithBestScore(Long userId, Long testId) {
        if (userId == null || testId == null) {
            return List.of();
        }
        FlashCardTest test = flashCardTestMapper.selectById(testId);
        if (test == null || !userId.equals(test.getUserId())) {
            return List.of();
        }

        // 题目模板来自该 testId 的题目表
        List<FlashCardTestQuestion> questions = flashCardTestQuestionMapper.selectList(
                new QueryWrapper<FlashCardTestQuestion>()
                        .eq("testId", testId)
                        .eq("isDelete", 0)
                        .orderByAsc("id"));
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }

        // 聚合：每题得分取历史最高（来自 attempt 快照）
        Map<Long, Integer> bestScoreByQuestionId = new HashMap<>();
        List<FlashCardTestAttempt> attempts = flashCardTestAttemptMapper.selectList(
                new QueryWrapper<FlashCardTestAttempt>()
                        .eq("userId", userId)
                        .eq("testId", testId)
                        .eq("isDelete", 0));
        if (attempts != null) {
            for (FlashCardTestAttempt a : attempts) {
                if (StringUtils.isBlank(a.getQuestionResultsJson())) {
                    continue;
                }
                try {
                    List<FlashCardTestQuestionResultVO> rs = objectMapper.readValue(
                            a.getQuestionResultsJson(),
                            new TypeReference<List<FlashCardTestQuestionResultVO>>() {});
                    for (FlashCardTestQuestionResultVO r : rs) {
                        if (r == null || r.getId() == null) {
                            continue;
                        }
                        int s = r.getUserScore() == null ? 0 : r.getUserScore();
                        bestScoreByQuestionId.merge(r.getId(), s, (oldV, newV) -> Math.max(oldV, newV));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        List<FlashCardTestQuestionResultVO> list = new ArrayList<>();
        for (FlashCardTestQuestion q : questions) {
            FlashCardTestQuestionResultVO vo = toQuestionResultVO(q);
            vo.setUserScore(bestScoreByQuestionId.getOrDefault(q.getId(), 0));
            list.add(vo);
        }
        // 保证稳定排序
        list.sort(Comparator.comparing(FlashCardTestQuestionResultVO::getId));
        return list;
    }

    @Override
    public List<FlashCardTestQuestionResultVO> getAttemptDetail(Long userId, Long attemptId) {
        if (userId == null || attemptId == null) {
            return List.of();
        }
        FlashCardTestAttempt attempt = flashCardTestAttemptMapper.selectById(attemptId);
        if (attempt == null || attempt.getIsDelete() != null && attempt.getIsDelete() == 1) {
            return List.of();
        }
        if (!userId.equals(attempt.getUserId())) {
            return List.of();
        }
        if (StringUtils.isBlank(attempt.getQuestionResultsJson())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    attempt.getQuestionResultsJson(),
                    new TypeReference<List<FlashCardTestQuestionResultVO>>() {});
        } catch (Exception e) {
            log.error("解析 attempt 逐题明细失败: attemptId={}, error={}", attemptId, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public FlashCardTestVO loadPaper(Long userId, Long testId) {
        if (userId == null || testId == null) {
            return null;
        }
        FlashCardTest test = flashCardTestMapper.selectById(testId);
        if (test == null || !userId.equals(test.getUserId()) || (test.getIsDelete() != null && test.getIsDelete() == 1)) {
            return null;
        }

        List<FlashCardTestQuestion> questions = flashCardTestQuestionMapper.selectList(
                new QueryWrapper<FlashCardTestQuestion>()
                        .eq("testId", testId)
                        .eq("isDelete", 0)
                        .orderByAsc("id"));
        if (questions == null || questions.isEmpty()) {
            return null;
        }

        FlashCardTestVO vo = new FlashCardTestVO();
        vo.setTestId(test.getId());
        vo.setNodeId(test.getNodeId());
        vo.setDifficulty(test.getDifficulty());

        List<FlashCardTestQuestionVO> qvos = new ArrayList<>();
        for (FlashCardTestQuestion q : questions) {
            FlashCardTestQuestionVO qvo = new FlashCardTestQuestionVO();
            qvo.setId(q.getId());
            qvo.setQuestionType(q.getQuestionType());
            qvo.setContent(q.getContent());
            qvo.setDifficulty(test.getDifficulty());
            qvo.setScore(q.getScore());
            if (StringUtils.isNotBlank(q.getOptions())) {
                try {
                    List<String> opts = objectMapper.readValue(q.getOptions(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    qvo.setOptions(opts);
                } catch (Exception e) {
                    qvo.setOptions(List.of());
                }
            } else {
                qvo.setOptions(List.of());
            }
            qvos.add(qvo);
        }
        vo.setQuestions(qvos);
        return vo;
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

    /**
     * 基于历史所有测试记录，按照“测试点 → 闪卡”规则重算某个闪卡的点亮状态。
     *
     * 规则：
     * - 每条 flashcard_test 记录视为一个“测试点实例”，score >= 60 的记为已点亮测试点
     * - litProgress = 已点亮测试点数量 / 总测试点数量 * 100（四舍五入取整）
     * - litStatus = litProgress >= 80
     *
     * 说明：
     * - 这里暂不区分 difficulty/多测试点模板，后续如需更细粒度可引入 testPointId 聚合表。
     */
    private void recomputeFlashCardLitFromTests(Long userId, String nodeId) {
        if (userId == null || StringUtils.isBlank(nodeId)) {
            return;
        }

        List<FlashCardTest> tests = flashCardTestMapper.selectList(
                new QueryWrapper<FlashCardTest>()
                        .eq("userId", userId)
                        .eq("nodeId", nodeId)
                        .eq("isDelete", 0)
                        .eq("status", "finished"));
        if (tests == null || tests.isEmpty()) {
            // 没有测试记录：视为未点亮
            neo4jFlashCardService.updateFlashCardLitStatus(
                    userId,
                    nodeId,
                    false,
                    0,
                    null,
                    System.currentTimeMillis()
            );
            neo4jFlashCardService.updateParentLitProgress(userId, nodeId);
            return;
        }

        int total = tests.size();
        int litCount = 0;
        int bestScore = 0;
        for (FlashCardTest t : tests) {
            int s = t.getScore() == null ? 0 : t.getScore();
            if (s >= 60) {
                litCount++;
            }
            if (s > bestScore) {
                bestScore = s;
            }
        }
        int litProgress = (int) Math.round(100.0 * litCount / total);
        boolean litStatus = litProgress >= 80;

        // 写入 Neo4j：score 字段沿用“最佳成绩”，litProgress 体现测试点点亮比例
        neo4jFlashCardService.updateFlashCardLitStatus(
                userId,
                nodeId,
                litStatus,
                bestScore,
                null,
                System.currentTimeMillis()
        );
        // 自底向上刷新父节点（层级节点 / 根节点）的点亮进度与状态（仍采用 80% 规则）
        neo4jFlashCardService.updateParentLitProgress(userId, nodeId);
    }
}

