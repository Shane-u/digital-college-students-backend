package com.digital.service.impl;

import com.digital.common.ErrorCode;
import com.digital.exception.BusinessException;
import com.digital.exception.ThrowUtils;
import com.digital.manager.SiliconFlowManager;
import com.digital.model.dto.chat.ChatRequest;
import com.digital.model.dto.chat.ChatResponse;
import com.digital.model.dto.chat.StreamChatResponse;
import com.digital.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.function.Consumer;

/**
 * 聊天服务实现类
 * 实现聊天功能的业务逻辑，包括参数校验和调用Manager层
 *
 * @author Shane
 */
@Service
@Slf4j
public class ChatServiceImpl implements ChatService {

    @Resource
    private SiliconFlowManager siliconFlowManager;

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        // 参数校验
        validateChatRequest(chatRequest);

        // 调用Manager层执行聊天请求
        return siliconFlowManager.chat(chatRequest);
    }

    @Override
    public void streamChat(ChatRequest chatRequest, Consumer<StreamChatResponse> onChunk) {
        // 参数校验
        validateChatRequest(chatRequest);

        // 调用Manager层执行流式聊天请求
        siliconFlowManager.streamChat(chatRequest, onChunk);
    }

    /**
     * 验证聊天请求参数
     *
     * @param chatRequest 聊天请求对象
     */
    private void validateChatRequest(ChatRequest chatRequest) {
        ThrowUtils.throwIf(chatRequest == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");

        // 验证消息列表
        List<com.digital.model.dto.chat.Message> messages = chatRequest.getMessages();
        ThrowUtils.throwIf(messages == null || messages.isEmpty(), 
                ErrorCode.PARAMS_ERROR, "消息列表不能为空");

        // 验证每条消息
        for (com.digital.model.dto.chat.Message message : messages) {
            ThrowUtils.throwIf(message == null, ErrorCode.PARAMS_ERROR, "消息对象不能为空");
            ThrowUtils.throwIf(StringUtils.isBlank(message.getRole()), 
                    ErrorCode.PARAMS_ERROR, "消息角色不能为空");
            ThrowUtils.throwIf(StringUtils.isBlank(message.getContent()), 
                    ErrorCode.PARAMS_ERROR, "消息内容不能为空");

            // 验证角色值
            String role = message.getRole();
            if (!"system".equals(role) && !"user".equals(role) && !"assistant".equals(role)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, 
                        "消息角色必须是 system、user 或 assistant 之一");
            }
        }

        // 验证temperature参数（如果提供了）
        Double temperature = chatRequest.getTemperature();
        if (temperature != null) {
            ThrowUtils.throwIf(temperature < 0 || temperature > 2, 
                    ErrorCode.PARAMS_ERROR, "temperature参数必须在0-2之间");
        }

        // 验证topP参数（如果提供了）
        Double topP = chatRequest.getTopP();
        if (topP != null) {
            ThrowUtils.throwIf(topP < 0 || topP > 1, 
                    ErrorCode.PARAMS_ERROR, "topP参数必须在0-1之间");
        }
    }
}

