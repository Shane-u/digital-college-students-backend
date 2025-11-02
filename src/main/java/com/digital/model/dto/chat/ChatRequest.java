package com.digital.model.dto.chat;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 聊天请求 DTO
 *
 * @author Shane
 */
@Data
public class ChatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息列表
     */
    private List<Message> messages;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 温度参数
     */
    private Double temperature;

    /**
     * 是否启用流式输出
     */
    private Boolean stream = true;

    /**
     * 最大生成令牌数
     */
    private Integer maxTokens;

    /**
     * Top-p 采样参数
     */
    private Double topP;
}

