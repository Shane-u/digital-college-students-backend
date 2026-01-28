package com.digital.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 闪卡生成事件
 * 用于异步通知闪卡生成成功或失败
 */
@Getter
public class FlashCardGeneratedEvent extends ApplicationEvent {
    
    private final Long flashCardId;
    private final Long userId;
    private final String status;
    private final String errorMessage;
    
    public FlashCardGeneratedEvent(Object source, Long flashCardId, Long userId, String status) {
        super(source);
        this.flashCardId = flashCardId;
        this.userId = userId;
        this.status = status;
        this.errorMessage = null;
    }
    
    public FlashCardGeneratedEvent(Object source, Long flashCardId, Long userId, String status, String errorMessage) {
        super(source);
        this.flashCardId = flashCardId;
        this.userId = userId;
        this.status = status;
        this.errorMessage = errorMessage;
    }
    
    public boolean isSuccess() {
        return "success".equals(status);
    }
    
    public boolean isFailed() {
        return "failed".equals(status);
    }
}
