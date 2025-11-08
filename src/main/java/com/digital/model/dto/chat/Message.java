package com.digital.model.dto.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 聊天消息 DTO
 * 支持多模态输入：文本、图片、视频
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
     * 消息内容（文本内容，兼容旧版本）
     * 如果使用多模态，请使用contentList
     */
    private String content;

    /**
     * 多模态内容列表
     * 支持文本、图片、视频
     * 格式：[{"type": "text", "text": "..."}, {"type": "image_url", "image_url": {"url": "..."}}]
     */
    @JsonProperty("content")
    private List<ContentItem> contentList;

    /**
     * 图片URL列表（简化方式，用于前端传参）
     */
    private List<String> imageUrls;

    /**
     * 视频URL列表（简化方式，用于前端传参）
     */
    private List<String> videoUrls;

    /**
     * 推理内容
     */
    @JsonProperty("reasoning_content")
    private String reasoningContent;

    /**
     * 内容项（用于多模态）
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentItem implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 内容类型：text/image_url/video_url
         */
        private String type;

        /**
         * 文本内容（当type为text时）
         */
        private String text;

        /**
         * 图片URL对象（当type为image_url时）
         */
        @JsonProperty("image_url")
        private ImageUrl imageUrl;

        /**
         * 视频URL对象（当type为video_url时）
         */
        @JsonProperty("video_url")
        private VideoUrl videoUrl;
    }

    /**
     * 图片URL对象
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImageUrl implements Serializable {
        private static final long serialVersionUID = 1L;
        private String url;
    }

    /**
     * 视频URL对象
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VideoUrl implements Serializable {
        private static final long serialVersionUID = 1L;
        private String url;
    }

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

    /**
     * 检查是否包含多模态内容
     *
     * @return true表示包含多模态内容
     */
    public boolean hasMultimodalContent() {
        return (imageUrls != null && !imageUrls.isEmpty()) ||
               (videoUrls != null && !videoUrls.isEmpty()) ||
               (contentList != null && !contentList.isEmpty());
    }
}

