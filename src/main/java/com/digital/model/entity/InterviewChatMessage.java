package com.digital.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * AI 面试会话聊天记录（实时语音 / 逐题回答的完整对话）
 * 存 MongoDB，用于报告分析时结合简历与对话内容生成总结。
 */
@Data
@Document(collection = "interview_chat_messages")
@CompoundIndex(name = "session_create", def = "{ 'sessionId': 1, 'createTime': 1 }")
public class InterviewChatMessage {

    @Id
    private String id;

    /** 面试会话 ID（与 MySQL ai_interview_session.id 一致，存字符串避免精度问题） */
    private String sessionId;
    private Long userId;
    /** user / assistant */
    private String role;
    private String content;
    /** 关联题目 ID，可选 */
    private Long questionId;
    /** 关联回答 ID，可选 */
    private Long answerId;
    private Date createTime;
}
