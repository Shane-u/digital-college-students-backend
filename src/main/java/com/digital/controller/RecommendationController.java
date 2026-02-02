package com.digital.controller;

import com.digital.common.BaseResponse;
import com.digital.common.ResultUtils;
import com.digital.model.entity.User;
import com.digital.model.vo.RecommendationVO;
import com.digital.service.RecommendationService;
import com.digital.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 推荐系统接口
 *
 * @author Shane
 */
@RestController
@RequestMapping("/recommendation")
@Slf4j
public class RecommendationController {

    @Resource
    private RecommendationService recommendationService;

    @Resource
    private UserService userService;

    /**
     * 记录用户行为（浏览、点击、收藏等）
     *
     * @param itemType     物品类型：CONTEST(竞赛)/JOB(职业)
     * @param itemId       物品id
     * @param behaviorType 行为类型：VIEW(浏览)/CLICK(点击)/COLLECT(收藏)/APPLY(报名/申请)/SHARE(分享)
     * @param context      上下文信息（可选）
     * @param request      HTTP请求
     * @return 操作结果
     */
    @PostMapping("/behavior")
    public BaseResponse<Boolean> recordBehavior(
            @RequestParam String itemType,
            @RequestParam Long itemId,
            @RequestParam String behaviorType,
            @RequestParam(required = false) String context,
            HttpServletRequest request) {
        try {
            // 从session获取用户id
            Long userId = getUserIdFromRequest(request);
            if (userId == null) {
                return ResultUtils.error(401, "用户未登录");
            }

            recommendationService.recordUserBehavior(userId, itemType, itemId, behaviorType, context);
            return ResultUtils.success(true);
        } catch (Exception e) {
            log.error("记录用户行为失败", e);
            return ResultUtils.error(500, "记录用户行为失败: " + e.getMessage());
        }
    }

    /**
     * 获取竞赛推荐列表
     *
     * @param limit   推荐数量（默认10）
     * @param request HTTP请求
     * @return 推荐列表
     */
    @GetMapping("/contest")
    public BaseResponse<List<RecommendationVO>> getContestRecommendations(
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            HttpServletRequest request) {
        try {
            Long userId = getUserIdFromRequest(request);
            if (userId == null) {
                return ResultUtils.error(401, "用户未登录");
            }

            List<RecommendationVO> recommendations = recommendationService.getContestRecommendations(userId, limit);
            return ResultUtils.success(recommendations);
        } catch (Exception e) {
            log.error("获取竞赛推荐失败", e);
            return ResultUtils.error(500, "获取竞赛推荐失败: " + e.getMessage());
        }
    }

    /**
     * 获取职业推荐列表
     *
     * @param limit   推荐数量（默认10）
     * @param request HTTP请求
     * @return 推荐列表
     */
    @GetMapping("/job")
    public BaseResponse<List<RecommendationVO>> getJobRecommendations(
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            HttpServletRequest request) {
        try {
            Long userId = getUserIdFromRequest(request);
            if (userId == null) {
                return ResultUtils.error(401, "用户未登录");
            }

            List<RecommendationVO> recommendations = recommendationService.getJobRecommendations(userId, limit);
            return ResultUtils.success(recommendations);
        } catch (Exception e) {
            log.error("获取职业推荐失败", e);
            return ResultUtils.error(500, "获取职业推荐失败: " + e.getMessage());
        }
    }

    /**
     * 刷新推荐缓存
     *
     * @param itemType 物品类型：CONTEST(竞赛)/JOB(职业)
     * @param request  HTTP请求
     * @return 操作结果
     */
    @PostMapping("/refresh")
    public BaseResponse<Boolean> refreshRecommendations(
            @RequestParam String itemType,
            HttpServletRequest request) {
        try {
            Long userId = getUserIdFromRequest(request);
            if (userId == null) {
                return ResultUtils.error(401, "用户未登录");
            }

            recommendationService.refreshRecommendationCache(userId, itemType);
            return ResultUtils.success(true);
        } catch (Exception e) {
            log.error("刷新推荐缓存失败", e);
            return ResultUtils.error(500, "刷新推荐缓存失败: " + e.getMessage());
        }
    }

    /**
     * 从请求中获取用户ID
     */
    private Long getUserIdFromRequest(HttpServletRequest request) {
        try {
            User loginUser = userService.getLoginUser(request);
            return loginUser != null ? loginUser.getId() : null;
        } catch (Exception e) {
            log.warn("获取用户信息失败", e);
            return null;
        }
    }
}
