package com.digital.model.dto.chat;

import lombok.Data;

import java.io.Serializable;

/**
 * 前端发送的最终聊天消息请求
 *
 * @author Shane
 */
@Data
public class FinalChatMessageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话ID
     */
    private String chatId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 最终消息内容
     */
    private String finalContent;
}
