package com.digital.service;

/**
 * 邮箱服务接口
 */
public interface EmailService {
    /**
     * 发送邮件
     */
    void sendEmail(String to, String subject, String content);
    
    /**
     * 发送验证码邮件（使用HTML模板）
     */
    void sendVerificationCodeEmail(String to, String verificationCode);
    
    /**
     * 发送闪卡生成成功通知邮件
     */
    void sendFlashCardGeneratedEmail(String to, String flashCardTitle, String flashCardContent, String flashCardHtmlContent, String viewUrl);
    
    /**
     * 发送闪卡生成失败通知邮件
     */
    void sendFlashCardFailedEmail(String to, String errorMessage);
}
