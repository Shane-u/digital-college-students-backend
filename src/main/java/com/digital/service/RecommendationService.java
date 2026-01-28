package com.digital.service;

import com.digital.model.entity.UserBehavior;
import com.digital.model.vo.RecommendationVO;

import java.util.List;

/**
 * 推荐服务接口
 *
 * @author Shane
 */
public interface RecommendationService {

    /**
     * 记录用户行为
     *
     * @param userId       用户id
     * @param itemType     物品类型
     * @param itemId       物品id
     * @param behaviorType 行为类型
     * @param context      上下文信息
     */
    void recordUserBehavior(Long userId, String itemType, Long itemId, 
                           String behaviorType, String context);

    /**
     * 获取竞赛推荐列表
     *
     * @param userId 用户id
     * @param limit  推荐数量
     * @return 推荐列表
     */
    List<RecommendationVO> getContestRecommendations(Long userId, Integer limit);

    /**
     * 获取职业推荐列表
     *
     * @param userId 用户id
     * @param limit  推荐数量
     * @return 推荐列表
     */
    List<RecommendationVO> getJobRecommendations(Long userId, Integer limit);

    /**
     * 刷新推荐结果缓存
     *
     * @param userId   用户id
     * @param itemType 物品类型
     */
    void refreshRecommendationCache(Long userId, String itemType);
}