package com.digital.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MilvusConfig {

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private String port;

    @Bean
    public MilvusClientV2 milvusClientV2() {
        String uri = host + ":" + port;
        if (!uri.startsWith("http://") && !uri.startsWith("https://")) {
            uri = "http://" + uri;
        }
        return new MilvusClientV2(ConnectConfig.builder()
                .uri(uri)
                .build());
    }
}
