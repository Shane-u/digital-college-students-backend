package com.digital.model.dto.bailian;

import lombok.Data;

import java.io.Serializable;

/**
 * 百炼创建会话响应 DTO
 *
 * @author Shane
 */
@Data
public class BaiLianCreateChatResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 响应码
     */
    private Integer code;

    /**
     * 会话ID（chat_id）
     */
    private String data;

    /**
     * 响应消息
     */
    private String message;
}
