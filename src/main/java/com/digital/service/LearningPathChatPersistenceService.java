package com.digital.service;

import com.digital.model.entity.ChatMessage;
import com.digital.model.entity.ChatSession;
import com.digital.repository.ChatMessageRepository;
import com.digital.repository.ChatSessionRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 学习路径规划/修改过程的聊天记录持久化（MongoDB）
 * 复用 chat_sessions / chat_messages 两个集合
 */
@Service
public class LearningPathChatPersistenceService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LearningPathChatPersistenceService.class);

    @Resource
    private ChatSessionRepository chatSessionRepository;

    @Resource
    private ChatMessageRepository chatMessageRepository;

    public String getOrCreateSession(Long userId, String sessionId, String title) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        if (StringUtils.isNotBlank(sessionId)) {
            ChatSession existing = chatSessionRepository.findByIdAndUserId(sessionId, userId);
            if (existing != null && Boolean.FALSE.equals(existing.getIsDelete())) {
                return existing.getId();
            }
        }

        ChatSession session = new ChatSession();
        session.setId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setTitle(StringUtils.isNotBlank(title) ? title : "学习路径对话");
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        session.setIsDelete(false);
        ChatSession saved = chatSessionRepository.save(session);
        return saved.getId();
    }

    public void touchSession(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        try {
            Optional<ChatSession> sessionOpt = chatSessionRepository.findById(sessionId);
            if (sessionOpt.isEmpty()) {
                return;
            }
            ChatSession session = sessionOpt.get();
            session.setUpdateTime(LocalDateTime.now());
            chatSessionRepository.save(session);
        } catch (Exception e) {
            log.warn("更新会话时间失败: sessionId={}, err={}", sessionId, e.getMessage());
        }
    }

    public void saveUserMessage(String sessionId, Long userId, String content) {
        saveMessage(sessionId, userId, "user", content);
    }

    public void saveAssistantMessage(String sessionId, Long userId, String content) {
        saveMessage(sessionId, userId, "assistant", content);
    }

    private void saveMessage(String sessionId, Long userId, String role, String content) {
        if (userId == null || StringUtils.isBlank(sessionId)) {
            return;
        }
        if (StringUtils.isBlank(content)) {
            return;
        }
        try {
            ChatMessage msg = new ChatMessage();
            msg.setId(UUID.randomUUID().toString());
            msg.setSessionId(sessionId);
            msg.setUserId(userId);
            msg.setRole(role);
            msg.setContent(content);
            msg.setCreateTime(LocalDateTime.now());
            msg.setIsDelete(false);
            chatMessageRepository.save(msg);
        } catch (Exception e) {
            log.warn("保存消息失败: sessionId={}, userId={}, role={}, err={}",
                    sessionId, userId, role, e.getMessage());
        }
    }

    public static String defaultTitleFromPrompt(String prompt) {
        if (StringUtils.isBlank(prompt)) {
            return "学习路径对话";
        }
        String p = prompt.trim();
        if (p.length() > 20) {
            p = p.substring(0, 20) + "...";
        }
        return "学习路径：" + p;
    }
}

