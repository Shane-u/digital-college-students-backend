package com.digital.config;

import java.security.Principal;

/**
 * WebSocket Principal 实现
 * 标识 WebSocket 连接的用户
 */
public class WebSocketPrincipal implements Principal {
    
    private final String name;
    private final Long userId;
    
    public WebSocketPrincipal(String name, Long userId) {
        this.name = name;
        this.userId = userId;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    public Long getUserId() {
        return userId;
    }
}
