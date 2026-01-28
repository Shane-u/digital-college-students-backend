package com.digital.listener;

import com.digital.event.FlashCardGeneratedEvent;
import com.digital.model.entity.FlashCard;
import com.digital.model.entity.User;
import com.digital.service.EmailService;
import com.digital.service.FlashCardService;
import com.digital.service.UserService;
import com.digital.service.WebSocketNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 闪卡生成事件监听器
 * 处理闪卡生成成功或失败后的异步通知
 */
@Component
@Slf4j
public class FlashCardEventListener {
    
    @Resource
    private WebSocketNotificationService webSocketNotificationService;
    
    @Resource
    private EmailService emailService;
    
    @Resource
    private UserService userService;
    
    @Resource
    private FlashCardService flashCardService;
    
    @Value("${server.address:0.0.0.0}")
    private String serverAddress;
    
    @Value("${server.port:8121}")
    private int serverPort;
    
    /**
     * 监听闪卡生成成功事件
     */
    @EventListener
    @Async
    public void handleFlashCardGenerated(FlashCardGeneratedEvent event) {
        if (event.isSuccess()) {
            webSocketNotificationService.sendFlashCardGeneratedNotification(
                    event.getUserId(), event.getFlashCardId());
            sendSuccessEmail(event);
        }
    }
    
    /**
     * 监听闪卡生成失败事件
     */
    @EventListener
    @Async
    public void handleFlashCardFailed(FlashCardGeneratedEvent event) {
        if (event.isFailed()) {
            webSocketNotificationService.sendFlashCardFailedNotification(
                    event.getUserId(), event.getFlashCardId(), event.getErrorMessage());
            sendFailedEmail(event);
        }
    }
    
    /**
     * 发送闪卡生成成功邮件
     */
    private void sendSuccessEmail(FlashCardGeneratedEvent event) {
        try {
            User user = userService.getById(event.getUserId());
            if (user == null || StringUtils.isBlank(user.getUserEmail())) {
                log.warn("发送闪卡生成成功邮件失败：用户不存在或邮箱为空，userId={}", event.getUserId());
                return;
            }
            
            FlashCard flashCard = flashCardService.getById(event.getFlashCardId());
            if (flashCard == null) {
                log.warn("发送闪卡生成成功邮件失败：闪卡不存在，flashCardId={}", event.getFlashCardId());
                return;
            }
            
            String viewUrl = buildFlashCardViewUrl();
            emailService.sendFlashCardGeneratedEmail(
                    user.getUserEmail(),
                    flashCard.getTitle(),
                    flashCard.getContent(),
                    flashCard.getHtmlContent(),
                    viewUrl
            );
            
            log.info("闪卡生成成功邮件已发送：userId={}, flashCardId={}, email={}", 
                    event.getUserId(), event.getFlashCardId(), user.getUserEmail());
        } catch (Exception e) {
            log.error("发送闪卡生成成功邮件异常：userId={}, flashCardId={}, error={}", 
                    event.getUserId(), event.getFlashCardId(), e.getMessage(), e);
        }
    }
    
    /**
     * 发送闪卡生成失败邮件
     */
    private void sendFailedEmail(FlashCardGeneratedEvent event) {
        try {
            User user = userService.getById(event.getUserId());
            if (user == null || StringUtils.isBlank(user.getUserEmail())) {
                log.warn("发送闪卡生成失败邮件失败：用户不存在或邮箱为空，userId={}", event.getUserId());
                return;
            }
            
            emailService.sendFlashCardFailedEmail(user.getUserEmail(), event.getErrorMessage());
            
            log.info("闪卡生成失败邮件已发送：userId={}, flashCardId={}, email={}", 
                    event.getUserId(), event.getFlashCardId(), user.getUserEmail());
        } catch (Exception e) {
            log.error("发送闪卡生成失败邮件异常：userId={}, flashCardId={}, error={}", 
                    event.getUserId(), event.getFlashCardId(), e.getMessage(), e);
        }
    }
    
    /**
     * 构建闪卡查看URL
     */
    private String buildFlashCardViewUrl() {
        String host = "localhost".equals(serverAddress) || "0.0.0.0".equals(serverAddress) 
                ? "localhost" : serverAddress;
        return String.format("http://%s:%d/api/flash-card.html", host, serverPort);
    }
}
