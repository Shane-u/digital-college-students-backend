package com.digital.service.aiinterview.impl;

import com.digital.model.entity.InterviewChatMessage;
import com.digital.repository.InterviewChatMessageRepository;
import com.digital.service.aiinterview.InterviewChatService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewChatServiceImpl implements InterviewChatService {

    private final InterviewChatMessageRepository repository;

    @Override
    public void appendMessage(Long sessionId, Long userId, String role, String content, Long questionId, Long answerId) {
        if (sessionId == null || StringUtils.isBlank(role)) {
            return;
        }
        if (StringUtils.isBlank(content)) {
            content = "";
        }
        // 简单去重：同会话同角色同内容在短时间内重复上报时只保留一条（避免实时语音同时写 chat 与 answers/text 再写一次）
        try {
            List<InterviewChatMessage> recent = repository.findBySessionIdOrderByCreateTimeAsc(String.valueOf(sessionId));
            if (recent != null && !recent.isEmpty()) {
                InterviewChatMessage last = recent.get(recent.size() - 1);
                if (last != null
                        && StringUtils.equalsIgnoreCase(last.getRole(), role)
                        && StringUtils.equals(last.getContent(), content)
                        && last.getCreateTime() != null
                        && (new Date().getTime() - last.getCreateTime().getTime()) <= 1500) {
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        InterviewChatMessage msg = new InterviewChatMessage();
        msg.setSessionId(String.valueOf(sessionId));
        msg.setUserId(userId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setQuestionId(questionId);
        msg.setAnswerId(answerId);
        msg.setCreateTime(new Date());
        repository.save(msg);
    }

    @Override
    public List<InterviewChatMessage> listBySessionId(Long sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        return repository.findBySessionIdOrderByCreateTimeAsc(String.valueOf(sessionId));
    }
}
