package com.digital.service.impl;

import com.digital.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 邮箱服务实现（支持HTML模板）
 */
@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Override
    public void sendEmail(String to, String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);
            mailSender.send(message);
            log.info("邮件发送成功，收件人：{}", to);
        } catch (Exception e) {
            log.error("邮件发送失败，收件人：{}，错误：{}", to, e.getMessage());
            throw new RuntimeException("邮件发送失败");
        }
    }
    
    /**
     * 发送验证码邮件（使用HTML模板）
     */
    @Override
    public void sendVerificationCodeEmail(String to, String verificationCode) {
        try {
            String htmlTemplate = loadEmailTemplate();
            String htmlContent = htmlTemplate.replace("{{VERIFICATION_CODE}}", verificationCode);
            sendEmail(to, "【数字大学生平台】验证码通知", htmlContent);
            
        } catch (Exception e) {
            log.error("发送验证码邮件失败，收件人：{}，错误：{}", to, e.getMessage());
            throw new RuntimeException("验证码邮件发送失败");
        }
    }
    
    /**
     * 发送闪卡生成成功通知邮件
     */
    @Override
    public void sendFlashCardGeneratedEmail(String to, String flashCardTitle, String flashCardContent, 
                                            String flashCardHtmlContent, String viewUrl) {
        try {
            String htmlContent = buildFlashCardSuccessEmailHtml(flashCardTitle, flashCardContent, flashCardHtmlContent, viewUrl);
            sendEmail(to, "【数字大学生平台】闪卡生成完成通知", htmlContent);
        } catch (Exception e) {
            log.error("发送闪卡生成成功邮件失败：to={}, error={}", to, e.getMessage(), e);
            throw new RuntimeException("闪卡生成成功邮件发送失败");
        }
    }
    
    /**
     * 发送闪卡生成失败通知邮件
     */
    @Override
    public void sendFlashCardFailedEmail(String to, String errorMessage) {
        try {
            String htmlContent = buildFlashCardFailedEmailHtml(errorMessage);
            sendEmail(to, "【数字大学生平台】闪卡生成失败通知", htmlContent);
        } catch (Exception e) {
            log.error("发送闪卡生成失败邮件失败：to={}, error={}", to, e.getMessage(), e);
            throw new RuntimeException("闪卡生成失败邮件发送失败");
        }
    }
    
    /**
     * 构建闪卡生成成功邮件HTML内容
     */
    private String buildFlashCardSuccessEmailHtml(String title, String content, String htmlContent, String viewUrl) {
        return String.format("""
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>闪卡生成完成</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: 'Microsoft YaHei', 'PingFang SC', 'Helvetica Neue', Arial, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        min-height: 100vh;
                        padding: 20px;
                    }
                    .email-container {
                        max-width: 600px;
                        margin: 0 auto;
                        background: #ffffff;
                        border-radius: 20px;
                        box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
                        overflow: hidden;
                    }
                    .header {
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        padding: 40px 30px;
                        text-align: center;
                        color: white;
                    }
                    .logo {
                        font-size: 32px;
                        font-weight: bold;
                        margin-bottom: 10px;
                    }
                    .content {
                        padding: 50px 30px;
                    }
                    .greeting {
                        font-size: 24px;
                        color: #333;
                        margin-bottom: 20px;
                        font-weight: 500;
                    }
                    .message {
                        font-size: 16px;
                        color: #666;
                        line-height: 1.6;
                        margin-bottom: 30px;
                    }
                    .flash-card-preview {
                        background: #f8f9fa;
                        border-radius: 15px;
                        padding: 30px;
                        margin: 30px 0;
                        border: 2px solid #e9ecef;
                    }
                    .flash-card-title {
                        font-size: 22px;
                        font-weight: bold;
                        color: #667eea;
                        margin-bottom: 20px;
                        text-align: center;
                    }
                    .flash-card-body {
                        color: #333;
                        line-height: 1.8;
                        font-size: 15px;
                    }
                    .view-button {
                        display: inline-block;
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        color: white;
                        text-decoration: none;
                        padding: 15px 40px;
                        border-radius: 25px;
                        font-size: 16px;
                        font-weight: 600;
                        margin: 30px 0;
                        text-align: center;
                        box-shadow: 0 10px 20px rgba(102, 126, 234, 0.3);
                        transition: transform 0.3s;
                    }
                    .view-button:hover {
                        transform: translateY(-2px);
                    }
                    .footer {
                        background: #f8f9fa;
                        padding: 30px;
                        text-align: center;
                        color: #666;
                        border-top: 1px solid #e9ecef;
                    }
                    .copyright {
                        font-size: 14px;
                        color: #999;
                        margin-top: 15px;
                    }
                </style>
            </head>
            <body>
                <div class="email-container">
                    <div class="header">
                        <div class="logo">🎓 数字大学生平台</div>
                        <div style="font-size: 16px; opacity: 0.9;">闪卡生成完成</div>
                    </div>
                    <div class="content">
                        <div class="greeting">您好！</div>
                        <div class="message">
                            您的记忆闪卡已成功生成，详情如下：
                        </div>
                        <div class="flash-card-preview">
                            <div class="flash-card-title">%s</div>
                            <div class="flash-card-body">%s</div>
                        </div>
                        <div style="text-align: center;">
                            <a href="%s" class="view-button">查看完整闪卡</a>
                        </div>
                        <div style="color: #666; font-size: 14px; margin-top: 30px; text-align: center;">
                            感谢您使用数字大学生平台！
                        </div>
                    </div>
                    <div class="footer">
                        <div style="font-size: 20px; font-weight: bold; color: #667eea; margin-bottom: 10px;">
                            🎓 数字大学生平台
                        </div>
                        <div class="copyright">
                            © 2025 数字大学生平台. All rights reserved.<br>
                            <strong>CopyRight：Shane</strong>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """, 
            escapeHtml(title), 
            escapeHtml(content.length() > 200 ? content.substring(0, 200) + "..." : content),
            viewUrl
        );
    }
    
    /**
     * 构建闪卡生成失败邮件HTML内容
     */
    private String buildFlashCardFailedEmailHtml(String errorMessage) {
        return String.format("""
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>闪卡生成失败</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: 'Microsoft YaHei', 'PingFang SC', 'Helvetica Neue', Arial, sans-serif;
                        background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%);
                        min-height: 100vh;
                        padding: 20px;
                    }
                    .email-container {
                        max-width: 600px;
                        margin: 0 auto;
                        background: #ffffff;
                        border-radius: 20px;
                        box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
                        overflow: hidden;
                    }
                    .header {
                        background: linear-gradient(135deg, #d32f2f 0%%, #f44336 100%%);
                        padding: 40px 30px;
                        text-align: center;
                        color: white;
                    }
                    .logo {
                        font-size: 32px;
                        font-weight: bold;
                        margin-bottom: 10px;
                    }
                    .content {
                        padding: 50px 30px;
                    }
                    .greeting {
                        font-size: 24px;
                        color: #333;
                        margin-bottom: 20px;
                        font-weight: 500;
                    }
                    .message {
                        font-size: 16px;
                        color: #666;
                        line-height: 1.6;
                        margin-bottom: 30px;
                    }
                    .error-box {
                        background: #ffebee;
                        border: 2px solid #f44336;
                        border-radius: 15px;
                        padding: 20px;
                        margin: 30px 0;
                        color: #c62828;
                    }
                    .error-message {
                        font-size: 15px;
                        line-height: 1.8;
                    }
                    .footer {
                        background: #f8f9fa;
                        padding: 30px;
                        text-align: center;
                        color: #666;
                        border-top: 1px solid #e9ecef;
                    }
                    .copyright {
                        font-size: 14px;
                        color: #999;
                        margin-top: 15px;
                    }
                </style>
            </head>
            <body>
                <div class="email-container">
                    <div class="header">
                        <div class="logo">🎓 数字大学生平台</div>
                        <div style="font-size: 16px; opacity: 0.9;">闪卡生成失败</div>
                    </div>
                    <div class="content">
                        <div class="greeting">您好！</div>
                        <div class="message">
                            很抱歉，您的记忆闪卡生成失败，详情如下：
                        </div>
                        <div class="error-box">
                            <div class="error-message">
                                <strong>错误信息：</strong><br>
                                %s
                            </div>
                        </div>
                        <div style="color: #666; font-size: 14px; margin-top: 30px; text-align: center;">
                            请稍后重试，如有问题请联系客服。<br>
                            感谢您使用数字大学生平台！
                        </div>
                    </div>
                    <div class="footer">
                        <div style="font-size: 20px; font-weight: bold; color: #667eea; margin-bottom: 10px;">
                            🎓 数字大学生平台
                        </div>
                        <div class="copyright">
                            © 2025 数字大学生平台. All rights reserved.<br>
                            <strong>CopyRight：Shane</strong>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """, 
            escapeHtml(errorMessage)
        );
    }
    
    /**
     * HTML转义
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    /**
     * 加载邮件模板
     */
    private String loadEmailTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource("templates/verification-email.html");
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("加载邮件模板失败：{}", e.getMessage());
            return "";
        }
    }
}
