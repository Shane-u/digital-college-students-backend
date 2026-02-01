package com.digital.example;

import com.digital.common.ErrorCode;
import com.digital.exception.BusinessException;
import com.digital.manager.SiliconFlowManager;
import com.digital.model.dto.chat.ChatRequest;
import com.digital.model.dto.chat.ChatResponse;
import com.digital.model.dto.chat.Message;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;


@Slf4j
@SpringBootTest
public class SiliconflowTest {

    @Autowired
    private SiliconFlowManager siliconFlowManager;

    @Test
    public void testSiliconflow() {
        // 调用硅基流动 AI 生成闪卡
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setModel("deepseek-ai/DeepSeek-V3.2"); // 使用默认模型
        chatRequest.setStream(false);
        chatRequest.setMessages(List.of(
                new Message("user", "你好")
        ));

        ChatResponse response = siliconFlowManager.chat(chatRequest);

        if (response == null || response.getContent() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI返回内容为空");
        }

        // 解析AI返回的JSON
        String responseContent = response.getContent();
        System.out.println(responseContent);
    }
}
