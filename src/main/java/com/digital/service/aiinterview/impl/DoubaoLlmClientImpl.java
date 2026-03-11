package com.digital.service.aiinterview.impl;

import com.digital.common.ErrorCode;
import com.digital.exception.BusinessException;
import com.digital.exception.ThrowUtils;
import com.digital.manager.DoubaoManager;
import com.digital.model.dto.chat.ChatRequest;
import com.digital.model.dto.chat.ChatResponse;
import com.digital.model.dto.chat.Message;
import com.digital.service.aiinterview.LlmClient;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DoubaoLlmClientImpl implements LlmClient {

    private final DoubaoManager doubaoManager;

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        ThrowUtils.throwIf(StringUtils.isBlank(userPrompt), ErrorCode.PARAMS_ERROR, "prompt 不能为空");
        ChatRequest req = new ChatRequest();
        req.setStream(false);

        List<Message> messages = new ArrayList<>();
        if (StringUtils.isNotBlank(systemPrompt)) {
            messages.add(new Message("system", systemPrompt));
        }
        messages.add(new Message("user", userPrompt));
        req.setMessages(messages);

        ChatResponse resp = doubaoManager.chat(req);
        if (resp == null || StringUtils.isBlank(resp.getContent())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "大模型返回为空");
        }
        return resp.getContent();
    }
}

