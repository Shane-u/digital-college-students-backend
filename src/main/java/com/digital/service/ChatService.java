package com.digital.service;

import com.digital.model.dto.chat.ChatRequest;
import com.digital.model.dto.chat.ChatResponse;
import com.digital.model.dto.chat.StreamChatResponse;

import java.util.function.Consumer;

/**
 * 聊天服务接口
 * 提供聊天功能的服务接口，支持流式和非流式两种模式
 *
 * @author Shane
 */
public interface ChatService {

    /**
     * 发送非流式聊天请求
     *
     * @param chatRequest 聊天请求对象
     * @return 聊天响应对象
     */
    ChatResponse chat(ChatRequest chatRequest);

    /**
     * 发送流式聊天请求
     *
     * @param chatRequest 聊天请求对象
     * @param onChunk 每收到一个数据块时的回调函数
     */
    void streamChat(ChatRequest chatRequest, Consumer<StreamChatResponse> onChunk);
}

