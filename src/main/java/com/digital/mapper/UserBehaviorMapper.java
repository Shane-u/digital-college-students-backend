package com.digital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.digital.model.entity.UserBehavior;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 用户行为Mapper
 *
 * @author Shane
 */
@Mapper
public interface UserBehaviorMapper extends BaseMapper<UserBehavior> {

    /**
     * 统计用户对物品的行为次数
     *
     * @param userId   用户id
     * @param itemType 物品类型
     * @param itemId   物品id
     * @return 行为统计
     */
    List<Map<String, Object>> countUserBehaviorByItem(
            @Param("userId") Long userId,
            @Param("itemType") String itemType,
            @Param("itemId") Long itemId
    );

    /**
     * 获取用户的行为历史（用于协同过滤）
     *
     * @param userId   用户id
     * @param itemType 物品类型
     * @param limit    限制数量
     * @return 行为列表
     */
    List<UserBehavior> getUserBehaviorHistory(
            @Param("userId") Long userId,
            @Param("itemType") String itemType,
            @Param("limit") Integer limit
    );

    /**
     * 获取相似用户（基于行为相似度）
     *
     * @param userId   用户id
     * @param itemType 物品类型
     * @param limit    限制数量
     * @return 相似用户列表（userId, similarity）
     */
    List<Map<String, Object>> getSimilarUsers(
            @Param("userId") Long userId,
            @Param("itemType") String itemType,
            @Param("limit") Integer limit
    );
}
