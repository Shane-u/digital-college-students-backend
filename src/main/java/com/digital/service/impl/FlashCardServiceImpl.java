package com.digital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.digital.common.ErrorCode;
import com.digital.exception.BusinessException;
import com.digital.exception.ThrowUtils;
import com.digital.manager.SiliconFlowManager;
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
import com.digital.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

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
    private UserService userService;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private ApplicationEventPublisher eventPublisher;

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
    public Long generateFlashCard(Long userId, FlashCardGenerateRequest request) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(request.getOriginalContent()), ErrorCode.PARAMS_ERROR, "原始内容不能为空");

        FlashCard flashCard = new FlashCard();
        flashCard.setUserId(userId);
        flashCard.setTitle("生成中...");
        flashCard.setContent("闪卡正在生成中，请稍候...");
        flashCard.setHtmlContent("<div style='padding: 20px; text-align: center;'><p>闪卡正在生成中，请稍候...</p></div>");
        flashCard.setOriginalContent(request.getOriginalContent());
        flashCard.setReviewCount(0);
        flashCard.setDifficultyLevel(null);
        
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 1);
        flashCard.setNextReviewTime(calendar.getTime());

        boolean saved = this.save(flashCard);
        ThrowUtils.throwIf(!saved, ErrorCode.SYSTEM_ERROR, "创建闪卡记录失败");
        
        Long flashCardId = flashCard.getId();

        try {
            FlashCardService proxy = applicationContext.getBean(FlashCardService.class);
            proxy.generateFlashCardAsync(flashCardId, userId, request);
        } catch (Exception e) {
            log.error("启动异步生成任务失败：flashCardId={}, error={}", flashCardId, e.getMessage(), e);
            flashCard.setTitle("生成失败");
            flashCard.setContent("启动异步生成任务失败：" + e.getMessage());
            flashCard.setHtmlContent("<div style='padding: 20px; text-align: center; color: red;'><p>启动异步生成任务失败，请重试</p></div>");
            this.updateById(flashCard);
        }

        return flashCardId;
    }

    /**
     * 异步生成闪卡内容并更新记录
     */
    @Override
    @Async
    public void generateFlashCardAsync(Long flashCardId, Long userId, FlashCardGenerateRequest request) {
        try {
            String prompt = String.format(GENERATE_PROMPT_TEMPLATE, request.getOriginalContent());
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
            
            String title = jsonNode.path("title").asText("").trim();
            String content = jsonNode.path("content").asText("").trim();
            String htmlContent = jsonNode.path("htmlContent").asText("").trim();

            if (title.isEmpty() || title.equals("生成中...")) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI返回的标题无效");
            }
            if (content.isEmpty() && htmlContent.isEmpty()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI返回的内容为空");
            }

            FlashCard flashCard = this.getById(flashCardId);
            if (flashCard == null) {
                log.error("闪卡生成失败：记录不存在，flashCardId={}", flashCardId);
                return;
            }
            
            if (!"生成中...".equals(flashCard.getTitle())) {
                log.warn("闪卡状态已改变，跳过更新，flashCardId={}, title={}", flashCardId, flashCard.getTitle());
                return;
            }

            flashCard.setTitle(title);
            flashCard.setContent(content);
            flashCard.setHtmlContent(htmlContent);

            boolean updated = this.updateById(flashCard);
            if (updated) {
                log.info("闪卡生成成功：flashCardId={}, userId={}, title={}", flashCardId, userId, title);
                eventPublisher.publishEvent(new FlashCardGeneratedEvent(this, flashCardId, userId, "success"));
            } else {
                log.error("闪卡生成失败：数据库更新失败，flashCardId={}", flashCardId);
                eventPublisher.publishEvent(new FlashCardGeneratedEvent(this, flashCardId, userId, "failed", "数据库更新失败"));
                this.removeById(flashCardId);
            }
            
        } catch (Exception e) {
            log.error("闪卡生成失败：flashCardId={}, userId={}, error={}", flashCardId, userId, e.getMessage(), e);
            
            try {
                FlashCard flashCard = this.getById(flashCardId);
                if (flashCard != null && "生成中...".equals(flashCard.getTitle())) {
                    this.removeById(flashCardId);
                }
            } catch (Exception deleteException) {
                log.error("删除失败闪卡记录异常：flashCardId={}", flashCardId, deleteException);
            }
            
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            eventPublisher.publishEvent(new FlashCardGeneratedEvent(this, flashCardId, userId, "failed", errorMessage));
        }
    }

    @Override
    public List<FlashCardVO> getUserFlashCards(Long userId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");

        QueryWrapper<FlashCard> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        queryWrapper.ne("title", "生成中...");
        queryWrapper.ne("title", "生成失败");
        queryWrapper.orderByDesc("createTime");

        return this.list(queryWrapper).stream()
                .map(this::getFlashCardVO)
                .collect(Collectors.toList());
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
    public boolean updateFlashCard(Long userId, FlashCardUpdateRequest request) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        ThrowUtils.throwIf(request.getId() == null, ErrorCode.PARAMS_ERROR, "闪卡ID不能为空");

        FlashCard flashCard = this.getById(request.getId());
        ThrowUtils.throwIf(flashCard == null, ErrorCode.NOT_FOUND_ERROR, "闪卡不存在");
        ThrowUtils.throwIf(!flashCard.getUserId().equals(userId), ErrorCode.FORBIDDEN_ERROR, "无权限修改");

        if (StringUtils.isNotBlank(request.getTitle())) {
            flashCard.setTitle(request.getTitle());
        }
        if (StringUtils.isNotBlank(request.getContent())) {
            flashCard.setContent(request.getContent());
        }
        if (StringUtils.isNotBlank(request.getHtmlContent())) {
            flashCard.setHtmlContent(request.getHtmlContent());
        }

        return this.updateById(flashCard);
    }

    @Override
    public boolean deleteFlashCard(Long userId, Long flashCardId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        ThrowUtils.throwIf(flashCardId == null, ErrorCode.PARAMS_ERROR, "闪卡ID不能为空");

        FlashCard flashCard = this.getById(flashCardId);
        ThrowUtils.throwIf(flashCard == null, ErrorCode.NOT_FOUND_ERROR, "闪卡不存在");
        ThrowUtils.throwIf(!flashCard.getUserId().equals(userId), ErrorCode.FORBIDDEN_ERROR, "无权限删除");

        return this.removeById(flashCardId);
    }

    @Override
    public boolean reviewFlashCard(Long userId, FlashCardReviewRequest request) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        ThrowUtils.throwIf(request.getId() == null, ErrorCode.PARAMS_ERROR, "闪卡ID不能为空");
        ThrowUtils.throwIf(request.getDifficultyLevel() == null || 
                request.getDifficultyLevel() < 1 || request.getDifficultyLevel() > 4,
                ErrorCode.PARAMS_ERROR, "难度等级必须在1-4之间");

        FlashCard flashCard = this.getById(request.getId());
        ThrowUtils.throwIf(flashCard == null, ErrorCode.NOT_FOUND_ERROR, "闪卡不存在");
        ThrowUtils.throwIf(!flashCard.getUserId().equals(userId), ErrorCode.FORBIDDEN_ERROR, "无权限操作");

        flashCard.setDifficultyLevel(request.getDifficultyLevel());
        flashCard.setReviewCount(flashCard.getReviewCount() + 1);
        flashCard.setLastReviewTime(new Date());
        flashCard.setNextReviewTime(calculateNextReviewTime(request.getDifficultyLevel(), flashCard.getReviewCount()));

        return this.updateById(flashCard);
    }

    @Override
    public FlashCardVO aiAssistFlashCard(Long userId, FlashCardAIAssistRequest request) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        ThrowUtils.throwIf(request.getId() == null, ErrorCode.PARAMS_ERROR, "闪卡ID不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(request.getPrompt()), ErrorCode.PARAMS_ERROR, "提示词不能为空");

        FlashCard flashCard = this.getById(request.getId());
        ThrowUtils.throwIf(flashCard == null, ErrorCode.NOT_FOUND_ERROR, "闪卡不存在");
        ThrowUtils.throwIf(!flashCard.getUserId().equals(userId), ErrorCode.FORBIDDEN_ERROR, "无权限操作");

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

            this.updateById(flashCard);
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
        return flashCardVO;
    }

    @Override
    public String getFlashCardStatus(Long flashCardId) {
        if (flashCardId == null) {
            return "not_found";
        }
        
        FlashCard flashCard = this.getById(flashCardId);
        if (flashCard == null) {
            return "not_found";
        }
        
        String title = flashCard.getTitle();
        if ("生成中...".equals(title)) {
            return "generating";
        } else if ("生成失败".equals(title)) {
            return "failed";
        } else if (title != null && !title.isEmpty() && !"生成中...".equals(title) && !"生成失败".equals(title)) {
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

    /**
     * 根据艾宾浩斯曲线计算下次复习时间
     * 难度等级：1-重来（30秒），2-困难（6分钟），3-良好（10分钟），4-简单（4天）
     */
    private Date calculateNextReviewTime(int difficultyLevel, int reviewCount) {
        Calendar calendar = Calendar.getInstance();
        
        switch (difficultyLevel) {
            case 1:
                calendar.add(Calendar.SECOND, 30);
                break;
            case 2:
                calendar.add(Calendar.MINUTE, 6);
                break;
            case 3:
                calendar.add(Calendar.MINUTE, 10);
                break;
            case 4:
                calendar.add(Calendar.DAY_OF_MONTH, 4);
                break;
            default:
                calendar.add(Calendar.MINUTE, 1);
        }

        return calendar.getTime();
    }
}

