package com.digital.model.dto.bailian;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 百炼创建会话请求 DTO
 *
 * @author Shane
 */
@Data
public class BaiLianCreateChatRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 应用ID
     */
    @JsonProperty("application_id")
    private String applicationId;

    /**
     * 是否调试模式
     */
    @JsonProperty("is_debug")
    private Boolean isDebug = false;
}
