package com.digital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.digital.common.ErrorCode;
import com.digital.exception.BusinessException;
import com.digital.exception.ThrowUtils;
import com.digital.manager.SiliconFlowManager;
import com.digital.manager.FlashCardProgressManager;
import com.digital.mapper.FlashCardMapper;
import com.digital.model.dto.chat.ChatRequest;
import com.digital.model.dto.chat.ChatResponse;
import com.digital.model.dto.chat.Message;
import com.digital.model.dto.flashcard.FlashCardAIAssistRequest;
import com.digital.model.dto.flashcard.FlashCardGenerateRequest;
import com.digital.model.dto.flashcard.FlashCardReviewRequest;
import com.digital.model.dto.flashcard.FlashCardUpdateRequest;
import com.digital.model.entity.FlashCard;
import com.digital.model.vo.FlashCardVO;
import com.digital.event.FlashCardGeneratedEvent;
import com.digital.service.FlashCardService;
import com.digital.service.Neo4jFlashCardService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.digital.utils.SM2Algorithm;
import com.digital.utils.SM2Algorithm.Grade;
import com.digital.utils.SM2Algorithm.SM2Result;
import java.util.stream.IntStream;

/**
 * 记忆闪卡服务实现
 *
 * @author Shane
 */
@Service
@Slf4j
public class FlashCardServiceImpl extends ServiceImpl<FlashCardMapper, FlashCard>
        implements FlashCardService {

    @Resource
    private SiliconFlowManager siliconFlowManager;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${flashcard.temp.expiration-days:7}") // 默认7天
    private long tempFlashCardExpirationDays;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Resource
    private FlashCardProgressManager flashCardProgressManager;

    @Resource
    private Neo4jFlashCardService neo4jFlashCardService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 生成闪卡的提示词模板
    private static final String GENERATE_PROMPT_TEMPLATE = """
            你是一位专业的记忆闪卡设计师。请根据以下内容生成一个高质量的记忆闪卡。
            
            ========== 任务要求 ==========
            
            【1. 标题提取】
            - 从内容中提取核心知识点标题
            - 标题要求：简洁明了，准确概括，不超过20个汉字
            - 避免使用"关于"、"介绍"等冗余词汇
            - 直接点明核心概念或主题
            
            【2. 内容结构化】
            - 将知识点内容整理为结构化的纯文本格式
            - 使用清晰的层次结构（可用序号、分段、要点列表）
            - 内容要求：准确、完整、易于理解和记忆
            - 重点突出关键概念、定义、原理或步骤
            - 适当使用换行和分段，提高可读性
            
            【3. HTML闪卡设计规范】
            必须生成一个完整的、自包含的HTML文档，包含以下元素：
            
            a) 整体布局：
               - 使用响应式设计，适配不同屏幕尺寸
               - 卡片容器：圆角、阴影、内边距合理
               - 背景：使用纯色背景，颜色搭配协调
            
            b) 样式要求：
               - 标题区域：字体较大、加粗、颜色醒目
               - 内容区域：字体清晰、行高适中、易于阅读
               - 使用CSS3动画：可添加淡入、滑动、渐变等优雅动画效果
               - 配色方案：使用2-3种协调的渐变色，避免过于鲜艳
            
            c) SVG装饰元素（可选但推荐）：
               - 添加简洁的SVG图标或装饰性图形
               - 可使用SVG动画增强视觉效果
               - 保持简洁，不喧宾夺主
            
            d) 技术要求：
               - 所有样式必须内联在<style>标签中
               - HTML结构清晰，语义化标签
               - 确保在不同浏览器中正常显示
               - 避免使用外部资源依赖
            
            【4. 输出格式】
            必须严格按照以下JSON格式返回，不要包含任何其他文字说明：
            {
              "title": "知识点标题",
              "content": "结构化的知识点内容（纯文本，可包含换行）",
              "htmlContent": "<完整的HTML代码，包含<style>标签>"
            }
            
            ========== 原始内容 ==========
            %s
            
            ========== 重要提示 ==========
            1. 只返回JSON格式，不要包含markdown代码块标记
            2. 确保JSON格式完全正确，可被直接解析
            3. HTML代码中的引号需要正确转义
            4. 保持设计风格统一、专业、美观
            5. 内容要准确反映原始内容的核心知识点
            """;

    // AI辅助修改的提示词模板

    private static final String AI_ASSIST_PROMPT_TEMPLATE = """
            你是一位专业的记忆闪卡设计师。请根据用户要求修改以下闪卡。
            
            ========== 当前闪卡信息 ==========
            
            【标题】
            %s
            
            【内容】
            %s
            
            【HTML内容】
            %s
            
            ========== 用户修改要求 ==========
            %s
            
            ========== 修改要求 ==========
            
            【1. 标题修改】
            - 如果用户要求修改标题，请生成新的简洁标题（不超过20字）
            - 保持标题的准确性和概括性
            
            【2. 内容修改】
            - 根据用户要求调整内容结构、补充或精简信息
            - 保持内容的结构化和可读性
            - 确保修改后的内容准确、完整
            
            【3. HTML样式修改】
            - 如果用户要求修改样式，请更新HTML和CSS
            - 保持与生成模板相同的设计规范：
              * 响应式布局
              * 协调的配色方案
              * 优雅的动画效果
              * 清晰的层次结构
            - 所有样式必须内联在<style>标签中
            - 保持HTML代码的完整性和自包含性
            
            【4. 输出格式】
            必须严格按照以下JSON格式返回，不要包含任何其他文字说明：
            {
              "title": "修改后的知识点标题",
              "content": "修改后的结构化知识点内容（纯文本，可包含换行）",
              "htmlContent": "<修改后的完整HTML代码，包含<style>标签>"
            }
            
            ========== 重要提示 ==========
            1. 只返回JSON格式，不要包含markdown代码块标记
            2. 确保JSON格式完全正确，可被直接解析
            3. HTML代码中的引号需要正确转义
            4. 保持设计风格与生成模板一致
            5. 如果用户要求只修改部分内容，其他部分保持不变
            """;



    @Override
    public String generateFlashCard(Long userId, FlashCardGenerateRequest request) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(request.getOriginalContent()), ErrorCode.PARAMS_ERROR, "原始内容不能为空");

        // 生成一个唯一的临时闪卡ID
        String tempFlashCardId = "temp_flashcard:" + java.util.UUID.randomUUID();
        
        FlashCard flashCard = new FlashCard();
        flashCard.setId(tempFlashCardId); // 设置ID为临时闪卡ID
        flashCard.setUserId(userId);
        flashCard.setTitle("生成中...");
        flashCard.setContent("闪卡正在生成中，请稍候...");
        flashCard.setHtmlContent("<div style='padding: 20px; text-align: center;'><p>闪卡正在生成中，请稍候...</p></div>");
        flashCard.setOriginalContent(request.getOriginalContent());
        flashCard.setRepetition(0);
        flashCard.setDifficultyLevel(null);
        flashCard.setEf(SM2Algorithm.INITIAL_EF); // 初始化 EF
        flashCard.setInterval(0); // 初始化间隔
        flashCard.setEf(SM2Algorithm.INITIAL_EF); // 初始化 EF
        flashCard.setInterval(0); // 初始化间隔
        
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 1);
        flashCard.setNextReviewTime(calendar.getTime());

        // 将初始闪卡信息保存到 Redis 暂存库
        redisTemplate.opsForValue().set(tempFlashCardId, flashCard, tempFlashCardExpirationDays, TimeUnit.DAYS);
        
        // 记录到用户的临时闪卡 Set 中
        String userTempSetKey = "user_temp_flashcards:" + userId;
        redisTemplate.opsForSet().add(userTempSetKey, tempFlashCardId);
        redisTemplate.expire(userTempSetKey, tempFlashCardExpirationDays, TimeUnit.DAYS); // 设置过期时间

        // 初始化进度跟踪，使用临时闪卡ID
        flashCardProgressManager.initProgress(tempFlashCardId, userId);

        try {
            FlashCardService proxy = applicationContext.getBean(FlashCardService.class);
            proxy.generateFlashCardAsync(tempFlashCardId, userId, request);
        } catch (Exception e) {
            log.error("启动异步生成任务失败：flashCardId={}, error={}", tempFlashCardId, e.getMessage(), e);
            // 更新进度为失败
            flashCardProgressManager.updateProgress(tempFlashCardId, "FAILED", 0, "启动异步生成任务失败：" + e.getMessage());
            // 更新 Redis 中的临时闪卡为失败状态
            flashCard.setId(tempFlashCardId); // 确保ID字段被设置
            flashCard.setTitle("生成失败");
            flashCard.setContent("启动异步生成任务失败：" + e.getMessage());
            flashCard.setHtmlContent("<div style='padding: 20px; text-align: center; color: red;'><p>启动异步生成任务失败，请重试</p></div>");
            redisTemplate.opsForValue().set(tempFlashCardId, flashCard, 7, java.util.concurrent.TimeUnit.DAYS); // 重新设置过期时间
        }

        return tempFlashCardId;
    }

    /**
     * 异步生成闪卡内容并更新记录
     */
    @Override
    @Async
    public void generateFlashCardAsync(String flashCardId, Long userId, FlashCardGenerateRequest request) {
        try {
            flashCardProgressManager.updateProgress(flashCardId, "AI_CALL_IN_PROGRESS", 20, "正在调用AI生成闪卡内容...");
            String prompt = String.format(GENERATE_PROMPT_TEMPLATE, request.getOriginalContent());
            ChatRequest chatRequest = new ChatRequest();
            chatRequest.setModel(null);
            chatRequest.setStream(false);
            chatRequest.setMessages(List.of(new Message("user", prompt)));
            // 调用siliconflow大模型
            ChatResponse response = siliconFlowManager.chat(chatRequest);
            if (response == null || response.getContent() == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI返回内容为空");
            }

            flashCardProgressManager.updateProgress(flashCardId, "PARSING_RESPONSE", 60, "AI内容返回，正在解析...");
            String responseContent = cleanJsonContent(response.getContent());
            JsonNode jsonNode = objectMapper.readTree(responseContent);
            
            String title = jsonNode.path("title").asText("").trim();
            String content = jsonNode.path("content").asText("").trim();
            String htmlContent = jsonNode.path("htmlContent").asText("").trim();

            if (title.isEmpty() || title.equals("生成中...")) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI返回的标题无效");
            }
            if (content.isEmpty() && htmlContent.isEmpty()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI返回的内容为空");
            }

            // 从 Redis 获取临时闪卡
            FlashCard flashCard = (FlashCard) redisTemplate.opsForValue().get(flashCardId);
            if (flashCard == null) {
                log.error("闪卡生成失败：Redis中不存在记录，flashCardId={}", flashCardId);
                // 任务完成，但闪卡记录不存在，移除进度
                flashCardProgressManager.removeProgress(flashCardId);
                return;
            }
            
            // 更新临时闪卡内容
            flashCard.setTitle(title);
            flashCard.setContent(content);
            flashCard.setHtmlContent(htmlContent);
            // 更新 Redis 中的临时闪卡
            redisTemplate.opsForValue().set(flashCardId, flashCard, tempFlashCardExpirationDays, java.util.concurrent.TimeUnit.DAYS); // 重新设置过期时间
            
            flashCardProgressManager.updateProgress(flashCardId, "COMPLETED", 100, "闪卡内容生成成功并暂存");
            eventPublisher.publishEvent(new FlashCardGeneratedEvent(this, flashCardId, userId, "success")); // 发布事件，通知闪卡已生成
            
        } catch (Exception e) {
            log.error("闪卡生成失败：flashCardId={}, userId={}, error={}", flashCardId, userId, e.getMessage(), e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            flashCardProgressManager.updateProgress(flashCardId, "FAILED", 0, "闪卡生成失败：" + errorMessage);

            // 更新 Redis 中的临时闪卡为失败状态
            FlashCard flashCard = (FlashCard) redisTemplate.opsForValue().get(flashCardId);
            if (flashCard != null) {
                flashCard.setId(flashCardId); // 确保ID字段被设置
                flashCard.setTitle("生成失败");
                flashCard.setContent("AI生成失败：" + errorMessage);
                flashCard.setHtmlContent("<div style='padding: 20px; text-align: center; color: red;'><p>AI生成失败，请重试</p></div>");
                redisTemplate.opsForValue().set(flashCardId, flashCard, tempFlashCardExpirationDays, java.util.concurrent.TimeUnit.DAYS); // 重新设置过期时间
            }
            
            eventPublisher.publishEvent(new FlashCardGeneratedEvent(this, flashCardId, userId, "failed", errorMessage));
        }
    }

    @Override
    public List<FlashCardVO> getUserFlashCards(Long userId) {
        QueryWrapper<FlashCard> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        queryWrapper.orderByDesc("createTime");
        List<FlashCard> flashCards = this.list(queryWrapper);
        return flashCards.stream().map(this::getFlashCardVO).collect(Collectors.toList());
    }


    /**
     * 获取用户的临时闪卡列表
     * 
     * 实现方案：使用 Redis Set 维护用户拥有的临时闪卡 key 列表
     * - 生成闪卡时：将 key 添加到 user_temp_flashcards:{userId} Set 中
     * - 获取闪卡时：从 Set 中获取所有 key，然后批量 get（高效）
     * - 删除闪卡时：从 Set 中移除对应的 key
     * 
     * @param userId 用户ID
     * @return 临时闪卡列表
     */
    public List<FlashCardVO> getTempUserFlashCards(Long userId) {
        String userTempSetKey = "user_temp_flashcards:" + userId;
        Set<Object> keys = redisTemplate.opsForSet().members(userTempSetKey);

        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        List<String> keyList = keys.stream().map(Object::toString).collect(Collectors.toList());
        List<Object> objects = redisTemplate.opsForValue().multiGet(keyList);

        if (objects == null) {
            return List.of();
        }

        // 用于收集已过期的 key，后续从 Set 中移除
        List<String> expiredKeys = new ArrayList<>();

        List<FlashCardVO> result = IntStream.range(0, objects.size())
                .filter(i -> {
                    // 过滤掉 null 值（已过期的闪卡）
                    if (objects.get(i) == null) {
                        expiredKeys.add(keyList.get(i));
                        return false;
                    }
                    return objects.get(i) instanceof FlashCard;
                })
                .mapToObj(i -> {
                    FlashCard flashCard = (FlashCard) objects.get(i);
                    FlashCardVO vo = getFlashCardVO(flashCard);
                    
                    // 计算过期天数
                    String flashCardKey = keyList.get(i);
                    Long expireTime = redisTemplate.getExpire(flashCardKey, TimeUnit.SECONDS);
                    
                    if (expireTime != null && expireTime > 0) {
                        // 将秒转换为天数（向上取整）
                        long days = (expireTime + 86400 - 1) / 86400; // 86400秒 = 1天
                        vo.setExpirationDays(days);
                    } else {
                        // 如果无法获取过期时间，使用配置的默认过期天数
                        vo.setExpirationDays(tempFlashCardExpirationDays);
                    }
                    
                    return vo;
                })
                .sorted((a, b) -> {
                    // 按创建时间倒序排序（如果有 createTime）
                    // 这里暂时保持原样，如果 FlashCardVO 有 createTime 字段可以改进
                    return 0;
                })
                .collect(Collectors.toList());

        // 清理已过期的 key（从 Set 中移除）
        if (!expiredKeys.isEmpty()) {
            redisTemplate.opsForSet().remove(userTempSetKey, expiredKeys.toArray());
            log.debug("已清理 {} 个过期的临时闪卡 key：userId={}", expiredKeys.size(), userId);
        }

        return result;
    }

    @Override
    public List<FlashCardVO> getReviewFlashCards(Long userId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");

        QueryWrapper<FlashCard> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        queryWrapper.le("nextReviewTime", new Date());
        queryWrapper.ne("title", "生成中...");
        queryWrapper.ne("title", "生成失败");
        queryWrapper.orderByAsc("nextReviewTime");

        return this.list(queryWrapper).stream()
                .map(this::getFlashCardVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateFlashCard(Long userId, FlashCardUpdateRequest request) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(request.getId()), ErrorCode.PARAMS_ERROR, "闪卡ID不能为空");

        String flashCardId = request.getId(); // request.getId() is now String

        // 使用QueryWrapper查询，确保类型转换正确（数据库是bigint，实体类是String）
        QueryWrapper<FlashCard> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", flashCardId);
        queryWrapper.eq("userId", userId);
        FlashCard flashCard = this.getOne(queryWrapper);
        ThrowUtils.throwIf(flashCard == null, ErrorCode.NOT_FOUND_ERROR, "闪卡不存在");

        // 保存更新前的值，用于 MySQL 与 Neo4j 同步
        String oldTitle = flashCard.getTitle();
        String oldContent = flashCard.getContent();
        String oldHierarchyPath = flashCard.getHierarchyPath();

        String newTitle = oldTitle;
        String newContent = oldContent;
        String newHierarchyPath = oldHierarchyPath;

        // 更新字段
        if (StringUtils.isNotBlank(request.getTitle())) {
            flashCard.setTitle(request.getTitle());
            newTitle = request.getTitle();
        }
        if (StringUtils.isNotBlank(request.getContent())) {
            flashCard.setContent(request.getContent());
            newContent = request.getContent();
        }
        if (StringUtils.isNotBlank(request.getHtmlContent())) {
            flashCard.setHtmlContent(request.getHtmlContent());
        }

        // 处理层级路径更新：根路径不能被修改
        if (StringUtils.isNotBlank(request.getHierarchyPath())) {
            newHierarchyPath = normalizeHierarchyPath(request.getHierarchyPath());

            if (StringUtils.isNotBlank(oldHierarchyPath)) {
                String normalizedOld = normalizeHierarchyPath(oldHierarchyPath);
                String oldRoot = extractRootFromHierarchyPath(normalizedOld);
                String newRoot = extractRootFromHierarchyPath(newHierarchyPath);

                if (oldRoot != null && newRoot != null && !oldRoot.equals(newRoot)) {
                    log.warn("尝试修改闪卡根路径被拒绝：userId={}, flashCardId={}, oldRoot={}, newRoot={}",
                            userId, flashCardId, oldRoot, newRoot);
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "根路径不能被修改");
                }
            }

            flashCard.setHierarchyPath(newHierarchyPath);
        }

        // 先更新 MySQL
        boolean updated = this.updateById(flashCard);
        if (!updated) {
            log.warn("MySQL 更新闪卡失败：userId={}, flashCardId={}", userId, request.getId());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新闪卡失败");
        }

        // MySQL 更新成功后，同步更新 Neo4j
        try {
            // 1. 同步内容及元数据到 Neo4j（无论是否变化，保证字段完整且 htmlContent 被清理）
            neo4jFlashCardService.updateFlashCardInNeo4j(
                    userId,
                    request.getId(),
                    newTitle,
                    newContent,
                    flashCard.getHierarchyPath(),
                    flashCard.getNextReviewTime() == null ? null : flashCard.getNextReviewTime().getTime(),
                    flashCard.getRepetition(),
                    flashCard.getDifficultyLevel(),
                    flashCard.getCreateTime() == null ? null : flashCard.getCreateTime().getTime(),
                    flashCard.getUpdateTime() == null ? null : flashCard.getUpdateTime().getTime()
            );
            log.info("闪卡 {} 内容及元数据已在 MySQL 和 Neo4j 中同步更新", request.getId());

            // 2. 如果层级路径发生变化，则更新 Neo4j 中的层级结构
            String normalizedOldPath = StringUtils.isBlank(oldHierarchyPath) ? null : normalizeHierarchyPath(oldHierarchyPath);
            String normalizedNewPath = StringUtils.isBlank(newHierarchyPath) ? null : normalizeHierarchyPath(newHierarchyPath);

            if (normalizedNewPath != null &&
                    (normalizedOldPath == null || !normalizedOldPath.equals(normalizedNewPath))) {
                neo4jFlashCardService.moveFlashCardToHierarchy(
                        userId,
                        normalizedOldPath,
                        normalizedNewPath,
                        flashCardId,
                        newTitle,
                        newContent,
                        flashCard.getHierarchyPath(),
                        flashCard.getNextReviewTime() == null ? null : flashCard.getNextReviewTime().getTime(),
                        flashCard.getRepetition(),
                        flashCard.getDifficultyLevel(),
                        flashCard.getCreateTime() == null ? null : flashCard.getCreateTime().getTime(),
                        flashCard.getUpdateTime() == null ? null : flashCard.getUpdateTime().getTime()
                );
                log.info("闪卡 {} 的层级路径已在 Neo4j 中更新：oldPath={}, newPath={}",
                        flashCardId, normalizedOldPath, normalizedNewPath);
            } else {
                log.debug("闪卡 {} 的层级路径未变化或未提供新路径，跳过 Neo4j 层级更新", flashCardId);
            }
        } catch (Exception e) {
            log.error("更新 Neo4j 中闪卡或层级失败：userId={}, flashCardId={}, error={}",
                    userId, request.getId(), e.getMessage(), e);
            // 抛出异常以触发事务回滚（回滚 MySQL 更新）
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "同步更新 Neo4j 失败：" + e.getMessage());
        }

        return true;
    }

    @Override
    public boolean deleteFlashCard(Long userId, String flashCardId) {
        // 数据库中的闪卡ID是Long类型，前端传过来的是String，需要转换
        Long flashCardLongId;
        try {
            flashCardLongId = Long.parseLong(flashCardId);
        } catch (NumberFormatException e) {
            // 如果不是有效的Long类型ID，可能是一个错误的ID或者其他问题
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "闪卡ID格式不正确");
        }

        // 使用QueryWrapper查询，确保类型转换正确（数据库是bigint，实体类是String）
        QueryWrapper<FlashCard> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", String.valueOf(flashCardLongId));
        queryWrapper.eq("userId", userId);
        FlashCard flashCard = this.getOne(queryWrapper);
        if (flashCard == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "闪卡不存在");
        }
        
        // 先删除MySQL中的数据（removeById也需要String类型）
        boolean removed = this.removeById(String.valueOf(flashCardLongId));
        if (removed) {
            // MySQL删除成功后，同步删除Neo4j中的数据
            try {
                neo4jFlashCardService.deleteFlashCardFromNeo4j(userId, flashCardId);
                log.info("闪卡 {} 已成功从 MySQL 和 Neo4j 中同步删除", flashCardId);
            } catch (Exception e) {
                log.error("从 Neo4j 删除闪卡失败：userId={}, flashCardId={}, error={}", userId, flashCardId, e.getMessage(), e);
                // Neo4j 删除失败不影响主流程，但记录错误日志
                // 注意：这里可以考虑添加重试机制或告警通知
            }
        } else {
            log.warn("MySQL 删除闪卡失败：userId={}, flashCardId={}", userId, flashCardId);
        }
        return removed;
    }


    @Override
    public boolean deleteTempFlashCard(Long userId, String tempFlashCardId) {
         // 验证所有权
         FlashCard flashCard = (FlashCard) redisTemplate.opsForValue().get(tempFlashCardId);
         if (flashCard != null && !flashCard.getUserId().equals(userId)) {
             throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
         }

         // 不需要检查是否存在，如果不存在就是成功删除
         redisTemplate.delete(tempFlashCardId);
         // 从用户的临时闪卡集合中移除
         String userTempSetKey = "user_temp_flashcards:" + userId;
         redisTemplate.opsForSet().remove(userTempSetKey, tempFlashCardId);

         // 移除进度信息
         flashCardProgressManager.removeProgress(tempFlashCardId);

         return true;
    }

    @Override
    public boolean updateTempFlashCard(Long userId, FlashCardUpdateRequest request) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(request.getId()), ErrorCode.PARAMS_ERROR, "闪卡ID不能为空");

        String flashCardId = request.getId();

        // 从 Redis 获取临时闪卡
        FlashCard flashCard = (FlashCard) redisTemplate.opsForValue().get(flashCardId);
        ThrowUtils.throwIf(flashCard == null, ErrorCode.NOT_FOUND_ERROR, "临时闪卡不存在或已过期");
        ThrowUtils.throwIf(!flashCard.getUserId().equals(userId), ErrorCode.FORBIDDEN_ERROR, "无权限修改该闪卡");

        // 更新字段
        if (StringUtils.isNotBlank(request.getTitle())) {
            flashCard.setTitle(request.getTitle());
        }
        if (StringUtils.isNotBlank(request.getContent())) {
            flashCard.setContent(request.getContent());
        }
        if (StringUtils.isNotBlank(request.getHtmlContent())) {
            flashCard.setHtmlContent(request.getHtmlContent());
        }

        // 更新 Redis 中的临时闪卡，并刷新过期时间
        redisTemplate.opsForValue().set(flashCardId, flashCard, tempFlashCardExpirationDays, TimeUnit.DAYS);
        log.info("临时闪卡 {} 已成功更新并刷新过期时间", flashCardId);

        return true;
    }

    @Override
    public boolean reviewFlashCard(Long userId, FlashCardReviewRequest request) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(request.getId()), ErrorCode.PARAMS_ERROR, "闪卡ID不能为空");
        ThrowUtils.throwIf(request.getDifficultyLevel() == null || 
                request.getDifficultyLevel() < 1 || request.getDifficultyLevel() > 4,
                ErrorCode.PARAMS_ERROR, "难度等级必须在1-4之间");

        // 使用QueryWrapper查询，确保类型转换正确（数据库是bigint，实体类是String）
        QueryWrapper<FlashCard> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", request.getId());
        queryWrapper.eq("userId", userId);
        FlashCard flashCard = this.getOne(queryWrapper);
        ThrowUtils.throwIf(flashCard == null, ErrorCode.NOT_FOUND_ERROR, "闪卡不存在");

        // 根据难度等级映射到 SM2Algorithm.Grade
        Grade grade;
        switch (request.getDifficultyLevel()) {
            case 1:
                grade = Grade.FAILED; // 重来 (0分)
                break;
            case 2:
                grade = Grade.HARD; // 困难 (1分)
                break;
            case 3:
                grade = Grade.NORMAL; // 良好 (3分)
                break;
            case 4:
                grade = Grade.EASY; // 简单 (4分)
                break;
            default:
                grade = Grade.FAILED; // 默认失败
                break;
        }

        // 获取当前闪卡的 SM-2 算法相关参数，如果为 null 则初始化
        int currentRepetition = flashCard.getRepetition() == null ? 0 : flashCard.getRepetition();
        double currentEf = flashCard.getEf() == null ? SM2Algorithm.INITIAL_EF : flashCard.getEf();
        int currentInterval = flashCard.getInterval() == null ? 0 : flashCard.getInterval();
        Date lastReviewTime = flashCard.getLastReviewTime() == null ? new Date() : flashCard.getLastReviewTime();

        // 调用 SM-2 算法计算新的复习参数
        SM2Result sm2Result = SM2Algorithm.calculate(grade, currentRepetition, currentEf, currentInterval, lastReviewTime);

        // 更新闪卡信息
        flashCard.setDifficultyLevel(request.getDifficultyLevel());
        flashCard.setRepetition(sm2Result.getRepetition());
        flashCard.setEf(sm2Result.getEf());
        flashCard.setInterval(sm2Result.getInterval());
        Date now = new Date();
        flashCard.setLastReviewTime(now); // 本次复习时间
        flashCard.setNextReviewTime(sm2Result.getNextReviewTime()); // 下次复习时间

        boolean updated = this.updateById(flashCard);

        // 同步复习相关信息到 Neo4j（如果存在对应节点）
        if (updated) {
            try {
                neo4jFlashCardService.updateFlashCardInNeo4j(
                        userId,
                        flashCard.getId(),
                        flashCard.getTitle(),
                        flashCard.getContent(),
                        flashCard.getHierarchyPath(),
                        flashCard.getNextReviewTime() == null ? null : flashCard.getNextReviewTime().getTime(),
                        flashCard.getRepetition(),
                        flashCard.getDifficultyLevel(),
                        flashCard.getCreateTime() == null ? null : flashCard.getCreateTime().getTime(),
                        flashCard.getUpdateTime() == null ? null : flashCard.getUpdateTime().getTime()
                );
            } catch (Exception e) {
                log.error("复习闪卡后同步 Neo4j 失败：userId={}, flashCardId={}, error={}",
                        userId, flashCard.getId(), e.getMessage(), e);
            }
        }

        return updated;
    }

    @Override
    public FlashCardVO aiAssistFlashCard(Long userId, FlashCardAIAssistRequest request) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(request.getId()), ErrorCode.PARAMS_ERROR, "闪卡ID不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(request.getPrompt()), ErrorCode.PARAMS_ERROR, "提示词不能为空");

        // 使用QueryWrapper查询，确保类型转换正确（数据库是bigint，实体类是String）
        QueryWrapper<FlashCard> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", request.getId());
        queryWrapper.eq("userId", userId);
        FlashCard flashCard = this.getOne(queryWrapper);
        ThrowUtils.throwIf(flashCard == null, ErrorCode.NOT_FOUND_ERROR, "闪卡不存在");

        try {
            String prompt = String.format(AI_ASSIST_PROMPT_TEMPLATE,
                    flashCard.getTitle(), flashCard.getContent(), flashCard.getHtmlContent(), request.getPrompt());

            ChatRequest chatRequest = new ChatRequest();
            chatRequest.setModel(null);
            chatRequest.setStream(false);
            chatRequest.setMessages(List.of(new Message("user", prompt)));

            ChatResponse response = siliconFlowManager.chat(chatRequest);
            if (response == null || response.getContent() == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI返回内容为空");
            }

            String responseContent = cleanJsonContent(response.getContent());
            JsonNode jsonNode = objectMapper.readTree(responseContent);

            flashCard.setTitle(jsonNode.path("title").asText(flashCard.getTitle()));
            flashCard.setContent(jsonNode.path("content").asText(flashCard.getContent()));
            flashCard.setHtmlContent(jsonNode.path("htmlContent").asText(flashCard.getHtmlContent()));

            // 更新MySQL
            boolean updated = this.updateById(flashCard);

            if (updated) {
                try {
                    // 同步更新Neo4j（包含内容、层级路径、复习信息和时间字段；不再存储 HTML）
                    neo4jFlashCardService.updateFlashCardInNeo4j(
                            userId,
                            flashCard.getId(),
                            flashCard.getTitle(),
                            flashCard.getContent(),
                            flashCard.getHierarchyPath(),
                            flashCard.getNextReviewTime() == null ? null : flashCard.getNextReviewTime().getTime(),
                            flashCard.getRepetition(),
                            flashCard.getDifficultyLevel(),
                            flashCard.getCreateTime() == null ? null : flashCard.getCreateTime().getTime(),
                            flashCard.getUpdateTime() == null ? null : flashCard.getUpdateTime().getTime()
                    );
                    log.info("AI辅助修改闪卡 {} 已成功在 MySQL 和 Neo4j 中同步更新", flashCard.getId());
                } catch (Exception e) {
                    log.error("AI辅助修改后，Neo4j 更新闪卡失败：userId={}, flashCardId={}, error={}", userId, request.getId(), e.getMessage(), e);
                    // Neo4j 更新失败不影响主流程，但记录错误日志
                }
            } else {
                log.warn("AI辅助修改后，MySQL 更新闪卡失败：userId={}, flashCardId={}", userId, request.getId());
            }
            return getFlashCardVO(flashCard);
        } catch (Exception e) {
            log.error("AI辅助修改闪卡失败：userId={}, flashCardId={}, error={}", userId, request.getId(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI辅助修改失败: " + e.getMessage());
        }
    }

    @Override
    public FlashCardVO getFlashCardVO(FlashCard flashCard) {
        if (flashCard == null) {
            return null;
        }
        FlashCardVO flashCardVO = new FlashCardVO();
        BeanUtils.copyProperties(flashCard, flashCardVO);
        // 实体中使用 repetition 字段，VO 中是 reviewCount，这里手动映射，避免为 null
        flashCardVO.setReviewCount(flashCard.getRepetition());
        // 层级路径直接透传，方便前端/图谱定位
        flashCardVO.setHierarchyPath(flashCard.getHierarchyPath());
        // 亮度相关字段目前由测试结果 / Neo4j 图谱计算，这里保持为空或默认值，前端可选择性使用
        flashCardVO.setLitStatus(null);
        flashCardVO.setLitScore(null);
        flashCardVO.setLitProgress(null);
        return flashCardVO;
    }

    @Override
    public String getFlashCardStatus(String flashCardId) {
        if (flashCardId == null) {
            return "not_found";
        }
        
        FlashCard flashCard = (FlashCard) redisTemplate.opsForValue().get(flashCardId); // 从Redis获取
        if (flashCard == null) {
            return "not_found";
        }
        
        String title = flashCard.getTitle();
        if ("生成中...".equals(title)) {
            return "generating";
        } else if ("生成失败".equals(title)) {
            return "failed";
        } else if (title != null && !title.isEmpty()) {
            return "success";
        } else {
            return "generating"; // 默认返回生成中
        }
    }

    /**
     * 清理AI返回的JSON内容，去除markdown代码块标记并修复JSON格式问题
     */
    private String cleanJsonContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }
        
        String cleaned = content.trim();
        
        // 去除markdown代码块标记 ```json ... ```
        if (cleaned.startsWith("```")) {
            int startIndex = cleaned.indexOf("\n");
            if (startIndex > 0) {
                cleaned = cleaned.substring(startIndex + 1);
            }
            int endIndex = cleaned.lastIndexOf("```");
            if (endIndex > 0) {
                cleaned = cleaned.substring(0, endIndex);
            }
            cleaned = cleaned.trim();
        }
        
        // 尝试修复JSON字符串中的未转义换行符
        try {
            // 先尝试直接解析，如果成功则直接返回
            objectMapper.readTree(cleaned);
            return cleaned;
        } catch (Exception e) {
            // 如果解析失败，尝试修复常见的JSON格式问题
            log.warn("JSON解析失败，尝试修复格式：{}", e.getMessage());
            
            // 修复策略：在JSON字符串值中转义未转义的换行符、回车符和制表符
            // 注意：只转义在字符串值中的控制字符，不破坏已转义的字符
            StringBuilder fixed = new StringBuilder();
            boolean inString = false;
            boolean escaped = false;
            
            for (int i = 0; i < cleaned.length(); i++) {
                char c = cleaned.charAt(i);
                
                if (escaped) {
                    // 如果当前字符是转义字符后的字符，直接添加
                    fixed.append(c);
                    escaped = false;
                    continue;
                }
                
                if (c == '\\') {
                    // 遇到反斜杠，标记为转义状态
                    fixed.append(c);
                    escaped = true;
                    continue;
                }
                
                if (c == '"') {
                    // 遇到引号，切换字符串状态
                    inString = !inString;
                    fixed.append(c);
                    continue;
                }
                
                if (inString) {
                    // 在字符串内部，转义未转义的控制字符
                    if (c == '\n') {
                        fixed.append("\\n");
                    } else if (c == '\r') {
                        fixed.append("\\r");
                    } else if (c == '\t') {
                        fixed.append("\\t");
                    } else if (c == '\b') {
                        fixed.append("\\b");
                    } else if (c == '\f') {
                        fixed.append("\\f");
                    } else {
                        fixed.append(c);
                    }
                } else {
                    // 不在字符串中，直接添加
                    fixed.append(c);
                }
            }
            
            String fixedContent = fixed.toString();
            
            // 再次尝试解析
            try {
                objectMapper.readTree(fixedContent);
                log.info("JSON格式修复成功");
                return fixedContent;
            } catch (Exception e2) {
                log.error("JSON格式修复后仍无法解析，原始错误：{}，修复后错误：{}", e.getMessage(), e2.getMessage());
                log.error("修复前的JSON内容（前500字符）：{}", 
                    cleaned.length() > 500 ? cleaned.substring(0, 500) + "..." : cleaned);
                log.error("修复后的JSON内容（前500字符）：{}", 
                    fixedContent.length() > 500 ? fixedContent.substring(0, 500) + "..." : fixedContent);
                // 如果修复后仍无法解析，抛出业务异常
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, 
                    "AI返回的JSON格式不正确，无法解析。错误信息：" + e.getMessage());
            }
        }
    }



    @Override
    public boolean confirmFlashCard(Long userId, String flashCardId, String hierarchyPath) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(flashCardId), ErrorCode.PARAMS_ERROR, "闪卡ID不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(hierarchyPath), ErrorCode.PARAMS_ERROR, "层级标签路径不能为空");

        // 从 Redis 获取临时闪卡
        FlashCard tempFlashCard = (FlashCard) redisTemplate.opsForValue().get(flashCardId);
        ThrowUtils.throwIf(tempFlashCard == null, ErrorCode.NOT_FOUND_ERROR, "临时闪卡不存在或已过期");
        ThrowUtils.throwIf(!tempFlashCard.getUserId().equals(userId), ErrorCode.FORBIDDEN_ERROR, "无权限确认该闪卡");

        // 检查闪卡是否已生成成功
        ThrowUtils.throwIf("生成中...".equals(tempFlashCard.getTitle()), ErrorCode.OPERATION_ERROR, "闪卡仍在生成中，请稍后再试");
        ThrowUtils.throwIf("生成失败".equals(tempFlashCard.getTitle()), ErrorCode.OPERATION_ERROR, "闪卡生成失败，无法保存");

        // 在保存到主库之前，确保用户的 Neo4j 数据库存在（如果不存在则创建）
        try {
            neo4jFlashCardService.ensureUserDatabaseExists(userId);
            log.debug("用户 Neo4j 数据库检查完成：userId={}", userId);
        } catch (Exception e) {
            log.error("确保用户 Neo4j 数据库存在失败：userId={}, error={}", userId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Neo4j 数据库准备失败，无法保存闪卡: " + e.getMessage());
        }

        // 保存到数据库
        // FlashCard 实体的主键策略是 ASSIGN_ID，因为 id 是 temp_ 开头的，置空让 MP 重新生成，
        // 存入 DB 时生成正式 ID
        // 为了避免 ID 冲突和格式问题，我们创建一个新对象
        FlashCard newFlashCard = new FlashCard();
        BeanUtils.copyProperties(tempFlashCard, newFlashCard); // Fix variable name from flashCard to tempFlashCard
        newFlashCard.setId(null); // 让 MyBatis Plus 生成新的 ID
        newFlashCard.setCreateTime(new Date());
        newFlashCard.setUpdateTime(new Date());
        // 将层级路径保存到 MySQL，便于后续更新时比较和校验
        newFlashCard.setHierarchyPath(normalizeHierarchyPath(hierarchyPath));

        boolean saved = this.save(newFlashCard);
        if (!saved) {
             throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存到数据库失败");
        }

        // 保存到 Neo4j
        try {
            neo4jFlashCardService.saveFlashCardToNeo4j(
                userId,
                hierarchyPath,
                newFlashCard.getTitle(),
                newFlashCard.getContent(),
                newFlashCard.getHierarchyPath(),
                newFlashCard.getNextReviewTime() == null ? null : newFlashCard.getNextReviewTime().getTime(),
                newFlashCard.getRepetition(),
                newFlashCard.getDifficultyLevel(),
                newFlashCard.getCreateTime() == null ? null : newFlashCard.getCreateTime().getTime(),
                newFlashCard.getUpdateTime() == null ? null : newFlashCard.getUpdateTime().getTime(),
                newFlashCard.getId()
            );

            // 邮件通知 / 事件通知：Neo4j 保存成功（可由监听器发送邮件）
            try {
                eventPublisher.publishEvent(new FlashCardGeneratedEvent(
                        this,
                        newFlashCard.getId(),
                        userId,
                        "success"
                ));
            } catch (Exception eventEx) {
                log.error("发布 Neo4j 保存成功事件时出错：userId={}, flashCardId={}, error={}",
                        userId, newFlashCard.getId(), eventEx.getMessage(), eventEx);
            }
        } catch (Exception e) {
            log.error("保存闪卡到 Neo4j 失败：userId={}, flashCardId={}, hierarchyPath={}, error={}",
                userId, newFlashCard.getId(), hierarchyPath, e.getMessage(), e);
            // Neo4j 保存失败不影响主流程，但记录错误日志
            // 使用事件通知机制（例如发送邮件），标记为 failed
            try {
                eventPublisher.publishEvent(new FlashCardGeneratedEvent(
                        this,
                        newFlashCard.getId(),
                        userId,
                        "failed",
                        "保存闪卡到 Neo4j 失败：" + e.getMessage()
                ));
            } catch (Exception eventEx) {
                log.error("发布 Neo4j 保存失败事件时出错：userId={}, flashCardId={}, error={}",
                        userId, newFlashCard.getId(), eventEx.getMessage(), eventEx);
            }
            // TODO: 如有需要可在此处回滚 MySQL 操作
        }

        // 删除 Redis 中的临时数据
        redisTemplate.delete(flashCardId);
        // 从用户的临时闪卡集合中移除
        String userTempSetKey = "user_temp_flashcards:" + userId;
        redisTemplate.opsForSet().remove(userTempSetKey, flashCardId);

        flashCardProgressManager.removeProgress(flashCardId);

        return true;
    }

    @Override
    public void deleteFlashCardHierarchy(Long userId, String hierarchyPath) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(hierarchyPath), ErrorCode.PARAMS_ERROR, "层级标签路径不能为空");

        try {
            neo4jFlashCardService.deleteFlashCardHierarchyFromNeo4j(userId, hierarchyPath);
            log.info("用户 {} 的闪卡层级 {} 已成功从 Neo4j 删除", userId, hierarchyPath);
        } catch (Exception e) {
            log.error("从 Neo4j 删除闪卡层级失败：userId={}, hierarchyPath={}, error={}",
                userId, hierarchyPath, e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除闪卡层级失败");
        }
    }

    /**
     * 规范化层级路径：去除首尾斜杠、合并多余斜杠
     */
    private String normalizeHierarchyPath(String hierarchyPath) {
        if (hierarchyPath == null) {
            return null;
        }
        String normalized = hierarchyPath.trim().replaceAll("^/+|/+$", "");
        // 合并中间可能的多个连续斜杠
        normalized = normalized.replaceAll("/+", "/");
        return normalized;
    }

    /**
     * 从层级路径中提取根节点名称（第一级）
     */
    private String extractRootFromHierarchyPath(String hierarchyPath) {
        if (StringUtils.isBlank(hierarchyPath)) {
            return null;
        }
        String normalized = normalizeHierarchyPath(hierarchyPath);
        String[] parts = normalized.split("/");
        return parts.length > 0 ? parts[0] : null;
    }

    @Override
    public void removeTempFlashCard(String flashCardId) {
        FlashCard flashCard = (FlashCard) redisTemplate.opsForValue().get(flashCardId);
        if (flashCard != null) {
             Long userId = flashCard.getUserId();
              String userTempSetKey = "user_temp_flashcards:" + userId;
             redisTemplate.opsForSet().remove(userTempSetKey, flashCardId);
        }
        redisTemplate.delete(flashCardId);
        flashCardProgressManager.removeProgress(flashCardId);
    }

    @Override
    public FlashCard getFlashCardByIdString(String flashCardId) {
        if (StringUtils.isBlank(flashCardId)) {
            return null;
        }

        // 优先从 Redis 获取临时闪卡
        if (flashCardId.startsWith("temp_flashcard:")) {
            FlashCard tempFlashCard = (FlashCard) redisTemplate.opsForValue().get(flashCardId);
            if (tempFlashCard != null) {
                return tempFlashCard;
            }
        }

        // 如果 Redis 中没有，或者不是临时闪卡，则尝试从数据库获取
        // 验证是否为有效的数字ID（数据库中的ID是bigint，但实体类使用String类型）
        try {
            // 验证ID是否为数字格式（用于数据库查询）
            Long.parseLong(flashCardId);
            // FlashCard的id字段是String类型，直接使用String类型的ID
            return this.getById(flashCardId);
        } catch (NumberFormatException e) {
            // 如果不是有效的Long类型ID，则说明不是数据库中的闪卡，也不是Redis中的临时闪卡
            return null;
        }
    }
}

