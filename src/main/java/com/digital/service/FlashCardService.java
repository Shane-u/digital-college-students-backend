package com.digital.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.digital.model.dto.flashcard.FlashCardAIAssistRequest;
import com.digital.model.dto.flashcard.FlashCardGenerateRequest;
import com.digital.model.dto.flashcard.FlashCardReviewRequest;
import com.digital.model.dto.flashcard.FlashCardUpdateRequest;
import com.digital.model.entity.FlashCard;
import com.digital.model.vo.FlashCardVO;
import java.util.List;

/**
 * 记忆闪卡服务
 */
public interface FlashCardService extends IService<FlashCard> {

    /**
     * 生成闪卡（异步）
     * 先创建"生成中"记录，后台异步生成内容
     */
    String generateFlashCard(Long userId, FlashCardGenerateRequest request);

    /**
     * 异步生成闪卡内容并更新记录
     */
    void generateFlashCardAsync(String flashCardId, Long userId, FlashCardGenerateRequest request);

    /**
     * 获取用户的闪卡列表
     */
    List<FlashCardVO> getUserFlashCards(Long userId);

    /**
     * 获取用户的暂存闪卡列表（Redis中）
     */
    List<FlashCardVO> getTempUserFlashCards(Long userId);

    /**
     * 获取需要复习的闪卡列表
     */
    List<FlashCardVO> getReviewFlashCards(Long userId);

    /**
     * 确认保存暂存闪卡
     */
    boolean confirmFlashCard(Long userId, String tempFlashCardId);

    /**
     * 删除暂存闪卡
     */
    boolean deleteTempFlashCard(Long userId, String tempFlashCardId);

    /**
     * 更新闪卡
     */
    boolean updateFlashCard(Long userId, FlashCardUpdateRequest request);

    /**
     * 删除闪卡
     */
    boolean deleteFlashCard(Long userId, String flashCardId);

    /**
     * 复习闪卡（更新复习时间和难度等级）
     */
    boolean reviewFlashCard(Long userId, FlashCardReviewRequest request);

    /**
     * AI辅助修改闪卡
     */
    FlashCardVO aiAssistFlashCard(Long userId, FlashCardAIAssistRequest request);

    /**
     * 获取闪卡VO
     */
    FlashCardVO getFlashCardVO(FlashCard flashCard);

    /**
     * 查询闪卡生成状态
     * 返回：generating-生成中, success-成功, failed-失败, not_found-不存在
     */
    String getFlashCardStatus(String flashCardId);

    void removeTempFlashCard(String flashCardId);

    FlashCard getFlashCardByIdString(String flashCardId);
}
