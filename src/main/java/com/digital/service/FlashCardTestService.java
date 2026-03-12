package com.digital.service;

import com.digital.model.dto.flashcard.FlashCardTestGenerateRequest;
import com.digital.model.dto.flashcard.FlashCardTestSubmitRequest;
import com.digital.model.vo.FlashCardTestResultVO;
import com.digital.model.vo.FlashCardTestVO;

/**
 * 闪卡 AI 测试服务
 */
public interface FlashCardTestService {

    /**
     * 生成测试题（调用豆包并落库）
     */
    FlashCardTestVO generateTest(Long userId, FlashCardTestGenerateRequest request);

    /**
     * 提交并批改测试，完成点亮与成长轨迹同步
     */
    FlashCardTestResultVO submitAndCorrect(Long userId, FlashCardTestSubmitRequest request);
}

