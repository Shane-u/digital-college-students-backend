package com.digital.controller;

import com.digital.common.BaseResponse;
import com.digital.common.ResultUtils;
import com.digital.model.dto.chat.ChatRequest;
import com.digital.model.dto.chat.ChatResponse;
import com.digital.model.dto.chat.ChatSessionRequest;
import com.digital.model.vo.ChatMessageVO;
import com.digital.model.vo.ChatSessionVO;
import com.digital.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 聊天控制器
 * 提供聊天功能的HTTP接口，支持流式和非流式两种模式
 *
 * @author Shane
 */
@RestController
@RequestMapping("/chat")
@Slf4j
public class ChatController {

    @Resource
    private ChatService chatService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 非流式聊天接口
     * 发送聊天请求并等待完整响应
     *
     * @param chatRequest 聊天请求对象
     * @return 聊天响应
     */
    @PostMapping("/completions")
    public BaseResponse<ChatResponse> chat(@RequestBody ChatRequest chatRequest) {
        // 确保使用非流式模式
        chatRequest.setStream(false);
        ChatResponse response = chatService.chat(chatRequest);
        return ResultUtils.success(response);
    }

    /**
     * 流式聊天接口
     * 使用Server-Sent Events (SSE) 实现流式输出
     *
     * @param chatRequest 聊天请求对象
     * @return SseEmitter 用于发送流式数据
     */
    @PostMapping("/completions/stream")
    public SseEmitter streamChat(@RequestBody ChatRequest chatRequest) {
        // 确保使用流式模式
        chatRequest.setStream(true);

        // 创建SseEmitter，设置超时时间为5分钟
        SseEmitter emitter = new SseEmitter(300000L);

        // 执行流式聊天
        chatService.streamChat(chatRequest, streamResponse -> {
            try {
                // 构建SSE格式的数据
                String data = "data: " + objectMapper.writeValueAsString(streamResponse) + "\n\n";
                
                // 发送数据
                emitter.send(SseEmitter.event()
                        .data(data)
                        .name("message"));

                log.debug("发送流式数据块: {}", streamResponse.getDeltaContent());

                // 如果流结束，关闭连接
                if (streamResponse.isFinished()) {
                    // 发送结束标记
                    emitter.send(SseEmitter.event()
                            .data("data: [DONE]\n\n")
                            .name("done"));
                    emitter.complete();
                    log.debug("流式响应完成");
                }
            } catch (IOException e) {
                log.error("发送流式数据失败", e);
                emitter.completeWithError(e);
            }
        });

        // 设置错误处理和超时处理
        emitter.onError(throwable -> {
            log.error("SseEmitter发生错误", throwable);
            emitter.completeWithError(throwable);
        });

        emitter.onTimeout(() -> {
            log.warn("SseEmitter超时");
            emitter.complete();
        });

        emitter.onCompletion(() -> {
            log.debug("SseEmitter完成");
        });

        return emitter;
    }

    /**
     * 流式聊天接口（纯文本格式）
     * 直接返回纯文本流，每个数据块单独一行
     *
     * @param chatRequest 聊天请求对象
     * @param response HTTP响应对象
     */
    @PostMapping("/completions/stream/text")
    public void streamChatText(@RequestBody ChatRequest chatRequest, 
                               HttpServletResponse response) {
        // 确保使用流式模式
        chatRequest.setStream(true);

        // 设置响应头
        response.setContentType("text/event-stream");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");

        try {
            // 执行流式聊天
            chatService.streamChat(chatRequest, streamResponse -> {
                try {
                    // 获取增量内容
                    String content = streamResponse.getDeltaContent();
                    if (StringUtils.isNotBlank(content)) {
                        // 直接写入响应流
                        response.getWriter().write("data: " + content + "\n\n");
                        response.getWriter().flush();
                        log.debug("发送流式文本: {}", content);
                    }

                    // 如果流结束
                    if (streamResponse.isFinished()) {
                        response.getWriter().write("data: [DONE]\n\n");
                        response.getWriter().flush();
                        log.debug("流式响应完成");
                    }
                } catch (IOException e) {
                    log.error("写入流式响应失败", e);
                }
            });
        } catch (Exception e) {
            log.error("流式聊天失败", e);
            try {
                response.getWriter().write("data: {\"error\":\"" + e.getMessage() + "\"}\n\n");
                response.getWriter().flush();
            } catch (IOException ex) {
                log.error("写入错误响应失败", ex);
            }
        }
    }

    /**
     * 创建聊天会话
     *
     * @param request 创建会话请求
     * @return 会话VO
     */
    @PostMapping("/sessions")
    public BaseResponse<ChatSessionVO> createSession(@RequestBody ChatSessionRequest request) {
        ChatSessionVO session = chatService.createSession(request);
        return ResultUtils.success(session);
    }

    /**
     * 获取用户的会话列表
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    @GetMapping("/sessions")
    public BaseResponse<List<ChatSessionVO>> getSessions(@RequestParam Long userId) {
        List<ChatSessionVO> sessions = chatService.getSessions(userId);
        return ResultUtils.success(sessions);
    }

    /**
     * 获取会话的消息列表
     *
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 消息列表
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public BaseResponse<List<ChatMessageVO>> getMessages(
            @PathVariable String sessionId,
            @RequestParam Long userId) {
        List<ChatMessageVO> messages = chatService.getMessages(sessionId, userId);
        return ResultUtils.success(messages);
    }

    /**
     * 删除会话（软删除）
     *
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @return 操作结果
     */
    @DeleteMapping("/sessions/{sessionId}")
    public BaseResponse<Void> deleteSession(
            @PathVariable String sessionId,
            @RequestParam Long userId) {
        chatService.deleteSession(sessionId, userId);
        return ResultUtils.success(null);
    }
}

