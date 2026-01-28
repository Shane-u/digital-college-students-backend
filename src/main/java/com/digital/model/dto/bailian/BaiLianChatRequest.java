package com.digital.model.dto.bailian;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 百炼聊天请求 DTO
 *
 * @author Shane
 */
@Data
public class BaiLianChatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话ID（chat_id）
     */
    @JsonProperty("chat_id")
    private String chatId;

    /**
     * 用户问题
     */
    private String question;

    /**
     * 是否启用流式输出
     */
    private Boolean stream = true;

    /**
     * 用户ID（用于消息持久化）
     */
    private Long userId;
}
