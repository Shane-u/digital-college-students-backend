package com.digital.example;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

// 这个注解会在 Spring 启动前加载 .env 文件
@Configuration
public class TestEnvConfig {
    static {
        // 加载项目根目录下的 .env 文件
        Dotenv dotenv = Dotenv.configure()
                .directory("./")  // .env 文件的路径（项目根目录）
                .load();
        
        // 将 .env 中的变量注入到 JVM 系统环境变量中，让 Spring 能读取到
        dotenv.entries().forEach(entry -> {
            System.setProperty(entry.getKey(), entry.getValue());
        });
    }
}