package com.digital.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "libretts")
@Data
public class LibreTtsConfig {

    private String baseUrl = "https://libretts.is-an.org/api/tts";

    private String voice = "zh-CN-XiaoxiaoNeural";

    private Integer rate = 0;

    private Integer pitch = 0;

    /**
     * 是否 preview（服务端参数）
     */
    private Boolean preview = false;
}

