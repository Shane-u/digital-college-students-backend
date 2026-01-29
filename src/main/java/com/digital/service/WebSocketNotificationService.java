package com.digital.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 通知服务
 * 用于向客户端发送闪卡生成状态通知
 *
 * @author Shane
 */
@Service
@Slf4j
public class WebSocketNotificationService {

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 发送闪卡生成成功通知
     */
    public void sendFlashCardGeneratedNotification(Long userId, String flashCardId) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "flashcard_generated");
            message.put("flashCardId", flashCardId);
            message.put("status", "success");
            message.put("message", "闪卡生成完成！");

            messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/flashcard", message);
            log.info("WebSocket通知发送成功：flashCardId={}, userId={}, 状态=成功", flashCardId, userId);
        } catch (Exception e) {
            log.error("WebSocket通知发送失败：flashCardId={}, userId={}, error={}", flashCardId, userId, e.getMessage(), e);
        }
    }

    /**
     * 发送闪卡生成失败通知
     */
    public void sendFlashCardFailedNotification(Long userId, String flashCardId, String errorMessage) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "flashcard_failed");
            message.put("flashCardId", flashCardId);
            message.put("status", "failed");
            message.put("error", errorMessage);
            message.put("message", "闪卡生成失败：" + errorMessage);

            messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/flashcard", message);
            log.info("WebSocket通知发送成功：flashCardId={}, userId={}, 状态=失败", flashCardId, userId);
        } catch (Exception e) {
            log.error("WebSocket通知发送失败：flashCardId={}, userId={}, error={}", flashCardId, userId, e.getMessage(), e);
        }
    }
}
