package com.digital.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Hugging Face ASR 配置
 */
@Configuration
@ConfigurationProperties(prefix = "huggingface.asr")
@Data
public class HuggingFaceAsrConfig {

    /**
     * Hugging Face API Token，例如：hf_xxx
     */
    private String apiKey;

    /**
     * ASR 模型名称，默认使用 Whisper large v3
     */
    private String model = "openai/whisper-large-v3";

    /**
     * Inference API 基础地址
     */
    private String baseUrl = "https://router.huggingface.co";
}


