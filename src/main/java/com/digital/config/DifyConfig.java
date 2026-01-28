package com.digital.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Dify 配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "dify")
public class DifyConfig {
    /**
     * API Key
     */
    private String apiKey;
    
    /**
     * Base URL，默认 https://api.dify.ai/v1
     */
    private String baseUrl = "https://api.dify.ai/v1";
    
    /**
     * 工作流 ID
     */
    private String workflowId;
    
    /**
     * 请求超时时间（毫秒），默认 60000
     */
    private Integer timeout = 60000;
}


