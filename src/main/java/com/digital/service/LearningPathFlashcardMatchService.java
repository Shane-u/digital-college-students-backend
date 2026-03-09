package com.digital.service;

import com.digital.model.dto.learningpath.LearningPathFlashcardMatchRequest;
import com.digital.model.vo.LearningPathFlashcardMatchVO;

/**
 * 学习路径 ↔ 闪卡图谱联动匹配服务
 */
public interface LearningPathFlashcardMatchService {

    /**
     * 点击学习路径节点后，根据“当前节点 + 全量后代”的关键词，全文检索匹配闪卡节点
     *
     * @param userId 用户 ID
     * @param pathId 学习路径 ID
     * @param request 匹配请求
     * @return 匹配结果；路径不存在或不属于用户则返回 null
     */
    LearningPathFlashcardMatchVO matchFlashcards(Long userId, String pathId, LearningPathFlashcardMatchRequest request);
}

