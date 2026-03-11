package com.digital.service.aiinterview;

public interface LlmClient {

    /**
     * 调用大模型并返回 assistant 的纯文本内容。
     */
    String complete(String systemPrompt, String userPrompt);
}

