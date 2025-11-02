package com.digital.model.dto.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 聊天消息 DTO
 *
 * @author Shane
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息角色
     * system: 系统消息
     * user: 用户消息
     * assistant: AI助手消息
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 推理内容
     */
    @JsonProperty("reasoning_content")
    private String reasoningContent;

    /**
     * 构造函数
     *
     * @param role 消息角色
     * @param content 消息内容
     */
    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }

    /**
     * 创建系统消息
     *
     * @param content 消息内容
     * @return Message 实例
     */
    public static Message system(String content) {
        return new Message("system", content);
    }

    /**
     * 创建用户消息
     *
     * @param content 消息内容
     * @return Message 实例
     */
    public static Message user(String content) {
        return new Message("user", content);
    }

    /**
     * 创建助手消息
     *
     * @param content 消息内容
     * @return Message 实例
     */
    public static Message assistant(String content) {
        return new Message("assistant", content);
    }
}

