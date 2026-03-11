package com.digital.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tencent.asr")
@Data
public class TencentAsrConfig {

    private String secretId;

    private String secretKey;

    private String region = "ap-shanghai";

    /**
     * 16k_zh / 16k_en 等，具体见腾讯云文档
     */
    private String engineModelType = "16k_zh";
}

