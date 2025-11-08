package com.digital.example;

import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import java.util.ArrayList;
import java.util.List;

/**
 * 这是一个示例类，展示了如何使用ArkService来完成聊天功能。
 */
public class ChatCompletionsExample {
    public static void main(String[] args) {
        // 从环境变量中获取API密钥
        String apiKey = System.getenv("ARK_API_KEY");        
        //  .
        ArkService arkService = ArkService.builder().apiKey(apiKey).baseUrl("https://ark.cn-beijing.volces.com/api/v3").build();
        
        // 初始化消息列表
        List<ChatMessage> chatMessages = new ArrayList<>();
        
        // 创建用户消息
        ChatMessage userMessage = ChatMessage.builder()
                .role(ChatMessageRole.USER) // 设置消息角色为用户
                .content("你是哪位？") // 设置消息内容
                .build();
        
        // 将用户消息添加到消息列表
        chatMessages.add(userMessage);
        
        // 创建聊天完成请求
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model("doubao-1-5-lite-32k-250115")// Get Model ID: https://www.volcengine.com/docs/82379/1330310 .
                .messages(chatMessages) // 设置消息列表
                .build();
        
        // 发送聊天完成请求并打印响应
        try {
            // 获取响应并打印每个选择的消息内容
            arkService.createChatCompletion(chatCompletionRequest)
                     .getChoices()
                     .forEach(choice -> System.out.println(choice.getMessage().getContent()));
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        } finally {
            // 关闭服务执行器
            arkService.shutdownExecutor();
        }
    }
}