package com.digital.service.aiinterview;

import com.digital.model.entity.InterviewChatMessage;

import java.util.List;

/**
 * AI 面试会话聊天记录（MongoDB），供实时语音与报告分析使用。
 */
public interface InterviewChatService {

    /**
     * 追加一条聊天消息（user 或 assistant）
     */
    void appendMessage(Long sessionId, Long userId, String role, String content, Long questionId, Long answerId);

    /**
     * 按会话查询聊天记录，按时间正序
     */
    List<InterviewChatMessage> listBySessionId(Long sessionId);
}
