package com.digital.service;

import com.digital.model.dto.bailian.BaiLianChatRequest;
import com.digital.model.dto.bailian.BaiLianCreateChatRequest;
import com.digital.model.dto.bailian.BaiLianCreateChatResponse;
import com.digital.model.dto.bailian.BaiLianStreamResponse;
import com.digital.model.entity.ChatMessage;
import com.digital.model.vo.ChatMessageVO;
import com.digital.repository.ChatMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 百炼服务
 *
 * @author Shane
 */
@Service
@Slf4j
public class BaiLianService {

    @Value("${bailian.api-key}")
    private String apiKey;

    @Value("${bailian.base-url}")
    private String baseUrl;

    @Value("${bailian.application-id}")
    private String applicationId;

    @Value("${bailian.timeout:60000}")
    private Long timeout;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private ChatMessageRepository chatMessageRepository;

    /**
     * 创建聊天会话（获取 chat_id）
     *
     * @return chat_id
     */
    public String createChat() {
        log.info("开始创建百炼聊天会话");

        BaiLianCreateChatRequest request = new BaiLianCreateChatRequest();
        request.setApplicationId(applicationId);
        request.setIsDebug(false);

        log.info("请求参数 - applicationId: {}, isDebug: {}", applicationId, false);

        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("AK", apiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            // 打印请求体用于调试
            String requestBody = objectMapper.writeValueAsString(request);
            log.info("请求体: {}", requestBody);

            BaiLianCreateChatResponse response = webClient.post()
                    .uri("/application/open_chat")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(BaiLianCreateChatResponse.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();

            if (response != null && response.getCode() == 200 && StringUtils.isNotBlank(response.getData())) {
                String chatId = response.getData();
                log.info("百炼聊天会话创建成功，chat_id: {}", chatId);
                return chatId;
            } else {
                log.error("创建百炼聊天会话失败，响应: {}", response);
                throw new RuntimeException("创建聊天会话失败: " + (response != null ? response.getMessage() : "未知错误"));
            }
        } catch (Exception e) {
            log.error("创建百炼聊天会话异常", e);
            throw new RuntimeException("创建聊天会话异常: " + e.getMessage(), e);
        }
    }

    /**
     * 流式聊天
     *
     * @param chatRequest 聊天请求
     * @return 流式响应
     */
    public Flux<BaiLianStreamResponse> streamChat(BaiLianChatRequest chatRequest) {
        log.info("开始百炼流式聊天，chat_id: {}, question: {}", chatRequest.getChatId(), chatRequest.getQuestion());

        if (StringUtils.isBlank(chatRequest.getChatId())) {
            return Flux.error(new IllegalArgumentException("chat_id 不能为空"));
        }

        if (StringUtils.isBlank(chatRequest.getQuestion())) {
            return Flux.error(new IllegalArgumentException("question 不能为空"));
        }

        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("AK", apiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            return webClient.post()
                    .uri("/application/chat")
                    .bodyValue(chatRequest)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(Duration.ofMillis(timeout))
                    .mapNotNull(line -> {
                        try {
                            if (StringUtils.isNotBlank(line)) {
                                // 解析 JSON 响应
                                return objectMapper.readValue(line, BaiLianStreamResponse.class);
                            }
                            return null;
                        } catch (Exception e) {
                            log.warn("解析百炼流式响应失败: {}", line, e);
                            return null;
                        }
                    })
                    .doOnComplete(() -> log.info("百炼流式聊天完成"))
                    .doOnError(e -> log.error("百炼流式聊天异常", e));
        } catch (Exception e) {
            log.error("百炼流式聊天异常", e);
            return Flux.error(new RuntimeException("流式聊天异常: " + e.getMessage(), e));
        }
    }

    /**
     * 流式聊天（回调方式）
     *
     * @param chatRequest 聊天请求
     * @param callback    回调函数
     */
    public void streamChatWithCallback(BaiLianChatRequest chatRequest, Consumer<BaiLianStreamResponse> callback) {
        streamChat(chatRequest)
                .subscribe(
                        callback,
                        error -> log.error("百炼流式聊天回调异常", error),
                        () -> log.debug("百炼流式聊天回调完成")
                );
    }

    /**
     * 流式聊天（回调方式，带消息持久化）
     *
     * @param chatRequest 聊天请求
     * @param callback    回调函数
     */
    public void streamChatWithCallbackAndSave(BaiLianChatRequest chatRequest, Consumer<BaiLianStreamResponse> callback) {
        Long userId = chatRequest.getUserId();
        String chatId = chatRequest.getChatId();
        String question = chatRequest.getQuestion();

        // 保存用户消息
        if (userId != null && StringUtils.isNotBlank(chatId) && StringUtils.isNotBlank(question)) {
            saveUserMessage(chatId, userId, question);
        }

        // 累积助手回复内容
        StringBuilder assistantContent = new StringBuilder();
        AtomicBoolean isAssistantMessageSaved = new AtomicBoolean(false);

        streamChat(chatRequest)
                .subscribe(
                        response -> {
                            // 累积内容
                            if (StringUtils.isNotBlank(response.getContent())) {
                                assistantContent.append(response.getContent());
                            }

                            // 调用原始回调
                            callback.accept(response);

                            // 如果流结束，保存助手消息
                            if (Boolean.TRUE.equals(response.getIsEnd()) && userId != null && StringUtils.isNotBlank(chatId)) {
                                String content = assistantContent.toString();
                                if (StringUtils.isNotBlank(content) && isAssistantMessageSaved.compareAndSet(false, true)) {
                                    saveAssistantMessage(chatId, userId, content);
                                }
                            }
                        },
                        error -> {
                            log.error("百炼流式聊天回调异常", error);
                            // 即使出错，也尝试保存已累积的内容
                            if (userId != null && StringUtils.isNotBlank(chatId)) {
                                String content = assistantContent.toString();
                                if (StringUtils.isNotBlank(content) && isAssistantMessageSaved.compareAndSet(false, true)) {
                                    saveAssistantMessage(chatId, userId, content);
                                }
                            }
                        },
                        () -> {
                            log.debug("百炼流式聊天回调完成");
                            // 确保保存助手消息（如果还没有保存）
                            if (userId != null && StringUtils.isNotBlank(chatId)) {
                                String content = assistantContent.toString();
                                if (StringUtils.isNotBlank(content) && isAssistantMessageSaved.compareAndSet(false, true)) {
                                    saveAssistantMessage(chatId, userId, content);
                                }
                            }
                        }
                );
    }

    /**
     * 保存用户消息
     */
    private void saveUserMessage(String sessionId, Long userId, String content) {
        if (userId == null) {
            log.error("用户ID为空，无法保存用户消息: sessionId={}", sessionId);
            return;
        }

        if (StringUtils.isBlank(content)) {
            log.warn("用户消息内容为空，跳过保存: sessionId={}, userId={}", sessionId, userId);
            return;
        }

        try {
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setId(UUID.randomUUID().toString());
            chatMessage.setSessionId(sessionId);
            chatMessage.setUserId(userId);
            chatMessage.setRole("user");
            chatMessage.setContent(content);
            chatMessage.setCreateTime(LocalDateTime.now());
            chatMessage.setIsDelete(false);

            ChatMessage saved = chatMessageRepository.save(chatMessage);
            log.info("保存用户消息成功: sessionId={}, messageId={}, userId={}, contentLength={}",
                    sessionId, saved.getId(), userId, content.length());
        } catch (Exception e) {
            log.error("保存用户消息失败: sessionId={}, userId={}, error={}",
                    sessionId, userId, e.getMessage(), e);
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 保存助手消息
     */
    private void saveAssistantMessage(String sessionId, Long userId, String content) {
        if (userId == null) {
            log.error("用户ID为空，无法保存助手消息: sessionId={}", sessionId);
            return;
        }

        if (StringUtils.isBlank(content)) {
            log.warn("助手消息内容为空，跳过保存: sessionId={}, userId={}", sessionId, userId);
            return;
        }

        try {
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setId(UUID.randomUUID().toString());
            chatMessage.setSessionId(sessionId);
            chatMessage.setUserId(userId);
            chatMessage.setRole("assistant");
            chatMessage.setContent(content);
            chatMessage.setCreateTime(LocalDateTime.now());
            chatMessage.setIsDelete(false);

            ChatMessage saved = chatMessageRepository.save(chatMessage);
            log.info("保存助手消息成功: sessionId={}, messageId={}, userId={}, contentLength={}",
                    sessionId, saved.getId(), userId, content.length());
        } catch (Exception e) {
            log.error("保存助手消息失败: sessionId={}, userId={}, error={}",
                    sessionId, userId, e.getMessage(), e);
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 获取会话的消息列表
     *
     * @param sessionId 会话ID（chatId）
     * @param userId    用户ID
     * @return 消息列表
     */
    public List<ChatMessageVO> getMessages(String sessionId, Long userId) {
        if (StringUtils.isBlank(sessionId)) {
            throw new IllegalArgumentException("会话ID不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        List<ChatMessage> messages = chatMessageRepository
                .findBySessionIdAndUserIdAndIsDeleteOrderByCreateTimeAsc(sessionId, userId, false);

        return messages.stream().map(msg -> {
            ChatMessageVO vo = new ChatMessageVO();
            BeanUtils.copyProperties(msg, vo);
            return vo;
        }).collect(Collectors.toList());
    }
}
