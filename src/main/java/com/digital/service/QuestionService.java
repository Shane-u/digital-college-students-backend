package com.digital.service;

import com.digital.model.vo.QuestionVO;
import java.util.List;

/**
 * 题目服务接口
 */
public interface QuestionService {

    /**
     * 获取MBTI问卷题目列表
     *
     * @param orderId 订单ID
     * @return 题目列表
     */
    List<QuestionVO> getQuestions(String orderId);
}
