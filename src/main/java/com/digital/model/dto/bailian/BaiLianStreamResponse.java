package com.digital.model.dto.bailian;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 百炼流式响应 DTO
 *
 * @author Shane
 */
@Data
public class BaiLianStreamResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话ID
     */
    @JsonProperty("chat_id")
    private String chatId;

    /**
     * 消息ID
     */
    private String id;

    /**
     * 回答类型
     */
    @JsonProperty("answer_type")
    private String answerType;

    /**
     * 是否结束
     */
    @JsonProperty("is_end")
    private Boolean isEnd;

    /**
     * 内容
     */
    private String content;

    /**
     * 节点ID
     */
    @JsonProperty("node_id")
    private String nodeId;

    /**
     * 节点名称
     */
    @JsonProperty("node_name")
    private String nodeName;

    /**
     * 节点图标
     */
    @JsonProperty("node_icon")
    private String nodeIcon;

    /**
     * 节点字典
     */
    @JsonProperty("node_dict")
    private Map<String, Object> nodeDict;

    /**
     * 开始节点参数
     */
    @JsonProperty("start_node_params")
    private List<Object> startNodeParams;

    /**
     * 是否为结果
     */
    @JsonProperty("is_result")
    private Boolean isResult;

    /**
     * ASCM产品链接
     */
    @JsonProperty("ascm_proudct_link")
    private String ascmProudctLink;

    /**
     * 节点类型
     */
    @JsonProperty("node_type")
    private String nodeType;

    /**
     * 段落列表
     */
    @JsonProperty("paragraph_list")
    private List<Object> paragraphList;

    /**
     * 错误信息
     */
    private String error;
}
