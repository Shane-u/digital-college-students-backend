package com.digital.service.impl;

import com.digital.model.vo.QuestionOptionVO;
import com.digital.model.vo.QuestionVO;
import com.digital.service.QuestionService;
import com.digital.utils.RedisCacheUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 题目服务实现
 */
@Service
@Slf4j
public class QuestionServiceImpl implements QuestionService {

    @Resource
    private OkHttpClient okHttpClient;

    @Resource
    private RedisCacheUtils redisCacheUtils;

    private static final String EXTERNAL_API_URL = "https://mbti.lingceu.com/api/order/251117133526897617/questions";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<QuestionVO> getQuestions(String orderId) {
        try {
            log.info("开始获取MBTI问卷题目");
            
            // 构建缓存key
            String cacheKey = RedisCacheUtils.CacheKey.QUESTION_PREFIX + (orderId != null ? orderId : "default");
            
            // 尝试从缓存获取
            @SuppressWarnings("unchecked")
            List<QuestionVO> cachedQuestions = redisCacheUtils.get(cacheKey);
            if (cachedQuestions != null && !cachedQuestions.isEmpty()) {
                log.debug("从缓存获取题目列表，orderId: {}", orderId);
                return cachedQuestions;
            }
            
            // 调用外部API
            String response = fetchFromExternalApi(EXTERNAL_API_URL);
            
            // 解析响应
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode dataNode = rootNode.get("data");
            
            List<QuestionVO> questions = new ArrayList<>();
            
            if (dataNode != null && dataNode.isArray()) {
                for (JsonNode questionNode : dataNode) {
                    QuestionVO question = parseQuestion(questionNode);
                    questions.add(question);
                }
            }
            
            // 缓存结果
            if (!questions.isEmpty()) {
                redisCacheUtils.set(cacheKey, questions, RedisCacheUtils.ExpireTime.QUESTION);
            }
            
            log.info("成功获取 {} 个题目", questions.size());
            return questions;
        } catch (Exception e) {
            log.error("获取MBTI问卷题目失败，订单ID: {}", orderId, e);
            throw new RuntimeException("获取题目失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从外部API获取数据
     */
    private String fetchFromExternalApi(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("API请求失败，状态码: " + response.code());
            }
            
            String body = response.body() != null ? response.body().string() : "";
            if (body.isEmpty()) {
                throw new RuntimeException("API返回空响应");
            }
            
            return body;
        }
    }

    /**
     * 解析单个题目
     */
    private QuestionVO parseQuestion(JsonNode questionNode) {
        QuestionVO question = new QuestionVO();
        
        // 设置题目基本信息
        if (questionNode.has("id")) {
            question.setId(questionNode.get("id").asLong());
        }
        if (questionNode.has("title")) {
            question.setTitle(questionNode.get("title").asText());
        }
        
        // 解析选项
        List<QuestionOptionVO> options = new ArrayList<>();
        if (questionNode.has("options") && questionNode.get("options").isArray()) {
            for (JsonNode optionNode : questionNode.get("options")) {
                QuestionOptionVO option = new QuestionOptionVO();
                
                if (optionNode.has("id")) {
                    option.setId(optionNode.get("id").asLong());
                }
                if (optionNode.has("title")) {
                    option.setTitle(optionNode.get("title").asText());
                }
                
                options.add(option);
            }
        }
        question.setOptions(options);
        
        return question;
    }
}
