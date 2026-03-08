package com.digital.model.dto.learningpath;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 学习路径 JSON 根结构
 * AI 输出、MySQL 存储、前后端交互的统一格式
 *
 * @author Shane
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LearningPathJson implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 节点列表
     */
    @JsonProperty("nodes")
    private List<LearningPathNode> nodes;
}
