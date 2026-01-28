package com.digital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.digital.model.entity.RecommendationResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 推荐结果Mapper
 *
 * @author Shane
 */
@Mapper
public interface RecommendationResultMapper extends BaseMapper<RecommendationResult> {

    /**
     * 获取用户的推荐结果
     *
     * @param userId   用户id
     * @param itemType 物品类型
     * @param limit    限制数量
     * @return 推荐结果列表
     */
    List<RecommendationResult> getUserRecommendations(
            @Param("userId") Long userId,
            @Param("itemType") String itemType,
            @Param("limit") Integer limit
    );

    /**
     * 删除过期的推荐结果
     *
     * @param expireTime 过期时间
     * @return 删除数量
     */
    int deleteExpiredRecommendations(@Param("expireTime") Date expireTime);
}
