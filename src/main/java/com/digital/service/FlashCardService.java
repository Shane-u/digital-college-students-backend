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
     * 获取需要复习的闪卡列表
     */
    List<FlashCardVO> getReviewFlashCards(Long userId);

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

    /**
     * 确认保存闪卡到终库
     * @param userId 用户ID
     * @param flashCardId 临时闪卡ID
     * @param hierarchyPath 层级标签路径，如 "root/课程/HTML" 或 "root/课程/前端/HTML"
     * @return 是否保存成功
     */
    boolean confirmFlashCard(Long userId, String flashCardId, String hierarchyPath);

    /**
     * 删除临时闪卡
     */
    void removeTempFlashCard(String flashCardId);


    /**
     * 更新临时闪卡
     */
    boolean updateTempFlashCard(Long userId, FlashCardUpdateRequest request);

    /**
     * 删除闪卡层级及其所有关联内容
     *
     * @param userId 用户ID
     * @param hierarchyPath 层级路径，如 "root/课程/HTML"
     */
    void deleteFlashCardHierarchy(Long userId, String hierarchyPath);

    /**
     * 根据String类型的闪卡ID获取闪卡（优先从Redis，然后从DB）
     */
    FlashCard getFlashCardByIdString(String flashCardId);

    /**
     * 获取用户的暂存（临时）闪卡列表
     *
     * @param userId 用户ID
     * @return 临时闪卡列表（VO）
     */
    List<FlashCardVO> getTempUserFlashCards(Long userId);

    /**
     * 删除临时闪卡及其进度
     *
     * @param userId         用户ID
     * @param tempFlashCardId 临时闪卡ID
     * @return 是否删除成功
     */
    boolean deleteTempFlashCard(Long userId, String tempFlashCardId);
}
