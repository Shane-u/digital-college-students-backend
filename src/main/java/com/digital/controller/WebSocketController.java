package com.digital.controller;

import com.digital.config.WebSocketPrincipal;
import com.digital.model.entity.User;
import com.digital.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;

import static com.digital.constant.UserConstant.USER_LOGIN_STATE;

/**
 * WebSocket 握手拦截器
 * 在 WebSocket 连接时进行用户认证
 */
@Component
@Slf4j
public class WebSocketController implements HandshakeInterceptor {

    @Resource
    private UserService userService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            HttpSession session = httpRequest.getSession(false);
            
            if (session != null) {
                Object userObj = session.getAttribute(USER_LOGIN_STATE);
                if (userObj instanceof User) {
                    User user = (User) userObj;
                    attributes.put("userId", user.getId());
                    attributes.put("user", user);
                    attributes.put("principal", new WebSocketPrincipal(String.valueOf(user.getId()), user.getId()));
                    return true;
                }
            }
            log.warn("WebSocket握手失败：用户未登录");
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                              WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("WebSocket握手异常", exception);
        }
    }
}
