package com.digital.config;

import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Neo4j 配置类
 *
 * @author Shane
 */
@Configuration
public class Neo4jConfig {

    @Value("${neo4j.uri}")
    private String uri;

    @Value("${neo4j.username}")
    private String username;

    @Value("${neo4j.password}")
    private String password;


    @Bean
    public Driver neo4jDriver() {
        return GraphDatabase.driver(uri, 
            org.neo4j.driver.AuthTokens.basic(username, password));
    }
}
