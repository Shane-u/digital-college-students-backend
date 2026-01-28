package com.digital.controller;

import com.digital.common.BaseResponse;
import com.digital.common.ResultUtils;

import com.digital.model.dto.bailian.BaiLianChatRequest;
import com.digital.model.entity.User;
import com.digital.model.vo.ChatMessageVO;
import com.digital.service.BaiLianService;
import com.digital.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 百炼聊天控制器
 * 提供百炼AI聊天功能的HTTP接口，支持流式输出
 */
@RestController
@RequestMapping("/bailian")
@Slf4j
public class BaiLianController {

    @Resource
    private BaiLianService baiLianService;

    @Resource
    private UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建聊天会话
     */
    @PostMapping("/chat/create")
    public BaseResponse<Map<String, String>> createChat() {
        try {
            String chatId = baiLianService.createChat();
            Map<String, String> result = new HashMap<>();
            result.put("chatId", chatId);
            return ResultUtils.success(result);
        } catch (Exception e) {
            log.error("创建聊天会话失败：error={}", e.getMessage(), e);
            return ResultUtils.error(500, "创建聊天会话失败: " + e.getMessage());
        }
    }

    /**
     * 流式聊天接口（SSE方式）
     */
    @PostMapping("/chat/stream")
    public SseEmitter streamChat(@RequestBody BaiLianChatRequest chatRequest, HttpServletRequest request) {
        if (StringUtils.isBlank(chatRequest.getChatId())) {
            return createErrorEmitter("错误: chat_id 不能为空");
        }

        if (StringUtils.isBlank(chatRequest.getQuestion())) {
            return createErrorEmitter("错误: question 不能为空");
        }

        // 解析用户ID
        Long userId = resolveUserId(request, chatRequest.getUserId());
        if (userId != null) {
            chatRequest.setUserId(userId);
        }

        chatRequest.setStream(true);
        SseEmitter emitter = new SseEmitter(300000L);

        // 使用带消息保存功能的方法
        baiLianService.streamChatWithCallbackAndSave(chatRequest, streamResponse -> {
            try {
                String json = objectMapper.writeValueAsString(streamResponse);
                emitter.send(SseEmitter.event().data(json).name("message"));

                if (Boolean.TRUE.equals(streamResponse.getIsEnd())) {
                    emitter.send(SseEmitter.event().data("[DONE]").name("done"));
                    emitter.complete();
                }
            } catch (IOException e) {
                log.error("发送流式数据失败：error={}", e.getMessage(), e);
                emitter.completeWithError(e);
            }
        });

        emitter.onError(throwable -> {
            log.error("SseEmitter异常：error={}", throwable.getMessage(), throwable);
            emitter.completeWithError(throwable);
        });

        emitter.onTimeout(() -> {
            log.warn("SseEmitter超时");
            emitter.complete();
        });

        return emitter;
    }

    /**
     * 基于WebFlux的流式接口
     */
    @PostMapping(value = "/chat/stream/flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChatFlux(@RequestBody BaiLianChatRequest chatRequest, HttpServletRequest request) {
        if (StringUtils.isBlank(chatRequest.getChatId())) {
            return Flux.just(ServerSentEvent.builder("错误: chat_id 不能为空").event("error").build());
        }

        if (StringUtils.isBlank(chatRequest.getQuestion())) {
            return Flux.just(ServerSentEvent.builder("错误: question 不能为空").event("error").build());
        }

        // 解析用户ID
        Long userId = resolveUserId(request, chatRequest.getUserId());
        if (userId != null) {
            chatRequest.setUserId(userId);
        }

        chatRequest.setStream(true);

        return Flux.<ServerSentEvent<String>>create(sink -> {
            Schedulers.boundedElastic().schedule(() -> {
                // 使用带消息保存功能的方法
                baiLianService.streamChatWithCallbackAndSave(chatRequest, streamResponse -> {
                    if (sink.isCancelled()) {
                        return;
                    }

                    String content = streamResponse.getContent();
                    if (StringUtils.isNotEmpty(content)) {
                        String encoded = content.replace(" ", "&#32;").replace("\n", "&#92n");
                        sink.next(ServerSentEvent.builder(encoded).event("message").build());
                    }

                    if (Boolean.TRUE.equals(streamResponse.getIsEnd())) {
                        sink.next(ServerSentEvent.builder("[DONE]").event("done").build());
                        sink.complete();
                    }
                });
            });
        }).publishOn(Schedulers.boundedElastic());
    }

    /**
     * 获取会话的消息列表
     *
     * @param chatId 会话ID（chat_id）
     * @param userId 用户ID
     * @return 消息列表
     */
    @GetMapping("/chat/{chatId}/messages")
    public BaseResponse<List<ChatMessageVO>> getMessages(
            @PathVariable String chatId,
            @RequestParam Long userId) {
        try {
            List<ChatMessageVO> messages = baiLianService.getMessages(chatId, userId);
            return ResultUtils.success(messages);
        } catch (Exception e) {
            log.error("获取消息列表失败：chatId={}, userId={}, error={}", chatId, userId, e.getMessage(), e);
            return ResultUtils.error(500, "获取消息列表失败: " + e.getMessage());
        }
    }

    /**
     * 解析用户ID
     * 优先从请求中获取登录用户，其次使用请求参数中的userId
     *
     * @param request HTTP请求
     * @param requestUserId 请求中的userId
     * @return 用户ID
     */
    @Nullable
    private Long resolveUserId(HttpServletRequest request, Long requestUserId) {
        Long resolvedUserId = null;
        try {
            User loginUser = userService.getLoginUser(request);
            if (loginUser != null) {
                resolvedUserId = loginUser.getId();
            }
        } catch (Exception ignored) {
            // 忽略异常，继续尝试其他方式
        }

        // 如果无法从请求中获取，使用请求参数中的userId
        if (resolvedUserId == null && requestUserId != null) {
            resolvedUserId = requestUserId;
        }

        return resolvedUserId;
    }

    /**
     * 创建错误响应Emitter
     */
    private SseEmitter createErrorEmitter(String errorMessage) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            emitter.send(SseEmitter.event().data(errorMessage).name("error"));
            emitter.complete();
        } catch (IOException e) {
            log.error("发送错误消息失败：error={}", e.getMessage(), e);
        }
        return emitter;
    }
}
