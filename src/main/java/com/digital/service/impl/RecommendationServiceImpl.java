package com.digital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.digital.mapper.*;
import com.digital.model.entity.*;
import com.digital.model.vo.RecommendationVO;
import com.digital.service.ContestService;
import com.digital.service.JobInfoService;
import com.digital.service.RecommendationService;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.common.IndexParam.MetricType; // MetricType，v2 SDK 的距离计算类型
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 推荐服务实现类
 *
 * @author Shane
 */
@Service
@Slf4j
public class RecommendationServiceImpl extends ServiceImpl<UserBehaviorMapper, UserBehavior> 
        implements RecommendationService {

    @Resource
    private UserBehaviorMapper userBehaviorMapper;

    @Resource
    private UserProfileMapper userProfileMapper;

    @Resource
    private RecommendationResultMapper recommendationResultMapper;

    @Resource
    private ContestMapper contestMapper;

    @Resource
    private JobInfoMapper jobInfoMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ContestService contestService;

    @Resource
    private JobInfoService jobInfoService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private MilvusClientV2 milvusClientV2;

    // Redis key前缀
    private static final String RECOMMENDATION_CACHE_PREFIX = "recommendation:";
    private static final String USER_PROFILE_CACHE_PREFIX = "user_profile:";
    
    // 推荐结果缓存时间（小时）
    private static final int RECOMMENDATION_CACHE_HOURS = 24;
    
    // 默认推荐数量
    private static final int DEFAULT_RECOMMENDATION_LIMIT = 10;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordUserBehavior(Long userId, String itemType, Long itemId, 
                                   String behaviorType, String context) {
        try {
            UserBehavior behavior = new UserBehavior();
            behavior.setUserId(userId);
            behavior.setItemType(itemType);
            behavior.setItemId(itemId);
            behavior.setBehaviorType(behaviorType);
            behavior.setContext(context);

            // 设置行为权重
            UserBehavior.BehaviorType type = Arrays.stream(UserBehavior.BehaviorType.values())
                    .filter(t -> t.getCode().equals(behaviorType))
                    .findFirst()
                    .orElse(UserBehavior.BehaviorType.VIEW);
            behavior.setBehaviorValue(BigDecimal.valueOf(type.getWeight()));

            // 保存行为记录
            this.save(behavior);

            // 清除用户画像缓存 (如果需要)
            String userProfileCacheKey = USER_PROFILE_CACHE_PREFIX + userId + ":" + itemType;
            redisTemplate.delete(userProfileCacheKey);

            // 清除推荐缓存，触发重新计算
            String cacheKey = RECOMMENDATION_CACHE_PREFIX + userId + ":" + itemType;
            redisTemplate.delete(cacheKey);

            log.info("记录用户行为成功: userId={}, itemType={}, itemId={}, behaviorType={}", 
                    userId, itemType, itemId, behaviorType);
        } catch (Exception e) {
            log.error("记录用户行为失败", e);
            throw new RuntimeException("记录用户行为失败: " + e.getMessage());
        }
    }

    @Override
    public List<RecommendationVO> getContestRecommendations(Long userId, Integer limit) {
        if (limit == null || limit <= 0) {
            limit = DEFAULT_RECOMMENDATION_LIMIT;
        }

        log.info("获取竞赛推荐请求: userId={}, limit={}", userId, limit);

        // 先尝试从缓存获取
        String cacheKey = RECOMMENDATION_CACHE_PREFIX + userId + ":CONTEST";
        @SuppressWarnings("unchecked")
        List<RecommendationVO> cached = (List<RecommendationVO>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            log.info("从缓存获取竞赛推荐结果: userId={}, count={}", userId, cached.size());
            return cached.stream().limit(limit).collect(Collectors.toList());
        }

        // 从 Milvus 获取推荐结果
        List<RecommendationVO> recommendations = getRecommendationsFromMilvus(userId, UserBehavior.ItemType.CONTEST.getCode(), limit);

        // 缓存推荐结果
        if (!recommendations.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, recommendations, 
                    RECOMMENDATION_CACHE_HOURS, TimeUnit.HOURS);
        }

        return recommendations;
    }

    @Override
    public List<RecommendationVO> getJobRecommendations(Long userId, Integer limit) {
        if (limit == null || limit <= 0) {
            limit = DEFAULT_RECOMMENDATION_LIMIT;
        }

        log.info("获取职业推荐请求: userId={}, limit={}", userId, limit);

        // 先尝试从缓存获取
        String cacheKey = RECOMMENDATION_CACHE_PREFIX + userId + ":JOB";
        @SuppressWarnings("unchecked")
        List<RecommendationVO> cached = (List<RecommendationVO>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            log.info("从缓存获取职业推荐结果: userId={}, count={}", userId, cached.size());
            return cached.stream().limit(limit).collect(Collectors.toList());
        }

        // 从 Milvus 获取推荐结果
        List<RecommendationVO> recommendations = getRecommendationsFromMilvus(userId, UserBehavior.ItemType.JOB.getCode(), limit);

        // 缓存推荐结果
        if (!recommendations.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, recommendations, 
                    RECOMMENDATION_CACHE_HOURS, TimeUnit.HOURS);
        }

        return recommendations;
    }


    /**
     * 从 Milvus 获取推荐结果
     * @param userId 用户ID
     * @param itemType 物品类型 (CONTEST, JOB)
     * @param limit 推荐数量
     * @return 推荐结果列表
     */
    private List<RecommendationVO> getRecommendationsFromMilvus(Long userId, String itemType, Integer limit) {
        try {
            // 1. 从 Milvus 获取用户 Embedding
            List<Float> userEmbedding = getUserEmbeddingFromMilvus(userId);
            if (userEmbedding == null || userEmbedding.isEmpty()) {
                log.warn("未找到用户Embedding: userId={}", userId);
                // 降级策略：例如返回热门物品或随机物品
                return getFallbackRecommendations(itemType, limit);
            }

            // 2. 在 Milvus 中执行向量相似度搜索
            // 根据物品类型确定集合名称，与Python训练脚本保持一致
            String collectionName;
            if (UserBehavior.ItemType.CONTEST.getCode().equals(itemType)) {
                collectionName = "contest_item_embeddings";
            } else if (UserBehavior.ItemType.JOB.getCode().equals(itemType)) {
                collectionName = "job_item_embeddings";
            } else {
                log.error("不支持的物品类型: {}", itemType);
                return getFallbackRecommendations(itemType, limit);
            }
            log.info("将在集合 {} 中进行搜索", collectionName);

            List<String> outFields = Arrays.asList("item_id"); // 我们只需要物品ID

            SearchReq searchReq = SearchReq.builder()
                    .collectionName(collectionName)
                    .data(Collections.singletonList(new FloatVec(userEmbedding)))
                    .annsField("embedding")
                    .topK(limit)
                    .outputFields(outFields)
                    .build();

            log.info("执行 Milvus 搜索请求: collectionName={}, annsField={}, topK={}, outputFields={}",
                    collectionName, "embedding", limit, outFields);

            SearchResp response = milvusClientV2.search(searchReq);
            log.info("Milvus 搜索响应: response={}", response);


            if (response == null || response.getSearchResults() == null || response.getSearchResults().isEmpty()) {
                log.error("Milvus搜索失败或无结果，执行降级策略: userId={}, itemType={}", userId, itemType); 
                return getFallbackRecommendations(itemType, limit);
            }

            List<SearchResp.SearchResult> results = response.getSearchResults().get(0);
            if (results.isEmpty()) {
                log.warn("Milvus搜索结果为空，执行降级策略: userId={}, itemType={}", userId, itemType);
                return getFallbackRecommendations(itemType, limit);
            }

            List<RecommendationVO> recommendations = new ArrayList<>();
            log.info("Milvus搜索返回 {} 个结果，开始处理...", results.size());

            for (SearchResp.SearchResult result : results) {
                Long itemId = (Long) result.getId();
                double distance = result.getScore();

                RecommendationVO vo = new RecommendationVO();
                vo.setItemId(itemId);
                vo.setScore(BigDecimal.valueOf(distance).setScale(6, RoundingMode.HALF_UP)); // Milvus 返回的是距离，小的距离代表高相似度
                vo.setAlgorithm(RecommendationResult.Algorithm.DUAL_TOWER.getCode());
                vo.setReason("基于您的兴趣和偏好推荐");

                // 3. 从数据库获取物品详细信息并设置到 RecommendationVO
                Object itemDetail = null;
                String itemName = null;

                if (UserBehavior.ItemType.CONTEST.getCode().equals(itemType)) {
                    Contest contest = contestMapper.selectById(itemId);
                    if (contest != null) {
                        itemName = contest.getContestName();
                        itemDetail = contestService.getContestVO(contest);
                    }
                } else if (UserBehavior.ItemType.JOB.getCode().equals(itemType)) {
                    JobInfo jobInfo = jobInfoMapper.selectById(itemId);
                    if (jobInfo != null) {
                        itemName = jobInfo.getWorkName();
                        itemDetail = jobInfo;
                    }
                }
                
                vo.setItemName(itemName);
                vo.setItemDetail(itemDetail);

                if (itemName != null) { // 只有找到物品详细信息才添加到推荐列表
                    recommendations.add(vo);
                    log.debug("添加推荐项: itemId={}, itemName={}, score={}", itemId, itemName, distance);
                } else {
                    log.warn("未在数据库中找到物品详细信息，跳过推荐项: itemId={}, itemType={}", itemId, itemType);
                }
            }

            // 根据距离排序 (距离越小越好)
            recommendations.sort(Comparator.comparing(RecommendationVO::getScore));
            
            log.info("从 Milvus 获取推荐结果成功: userId={}, itemType={}, 实际推荐数量={}", userId, itemType, recommendations.size());
            return recommendations.stream().limit(limit).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("从 Milvus 获取推荐结果失败: userId={}, itemType={}, 错误信息={}", userId, itemType, e.getMessage(), e);
            return getFallbackRecommendations(itemType, limit); // 降级策略
        }
    }

    /**
     * 从 Milvus 获取用户 Embedding
     * @param userId 用户ID
     * @return 用户 Embedding 向量
     */
    private List<Float> getUserEmbeddingFromMilvus(Long userId) {
        log.info("开始从 Milvus 获取用户Embedding: userId={}", userId);
        try {
            String collectionName = "user_embeddings";
            List<String> outputFields = Arrays.asList("embedding");
            String filterExpr = String.format("user_id == %d", userId);

            QueryReq queryReq = QueryReq.builder()
                    .collectionName(collectionName)
                    .filter(filterExpr)
                    .outputFields(outputFields)
                    .build();

            log.info("执行 Milvus Query 请求: collectionName={}, filter='{}', outputFields={}",
                    collectionName, filterExpr, outputFields);

            QueryResp response = milvusClientV2.query(queryReq);
            log.info("Milvus Query 响应: response={}", response);

            if (response == null || response.getQueryResults() == null || response.getQueryResults().isEmpty()) {
                log.error("查询用户Embedding失败或无结果: userId={}, Milvus响应为空或无结果", userId);
                return null;
            }

            // 获取查询结果
            QueryResp.QueryResult result = response.getQueryResults().get(0);
            Map<String, Object> entity = result.getEntity();

            if (entity == null || !entity.containsKey("embedding")) {
                log.error("Milvus响应中未包含'embedding'字段或实体为空: userId={}", userId);
                return null;
            }

            List<Float> embedding = (List<Float>) entity.get("embedding");
            if (embedding == null || embedding.isEmpty()) {
                log.error("获取到的用户Embedding为空: userId={}", userId);
            } else {
                log.info("成功获取用户Embedding: userId={}, 维度={}", userId, embedding.size());
            }
            return embedding;

        } catch (Exception e) {
            log.error("获取用户Embedding失败: userId={}, 错误信息={}", userId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 降级推荐策略：返回热门物品或随机物品
     * @param itemType 物品类型
     * @param limit 推荐数量
     * @return 推荐结果列表
     */
    private List<RecommendationVO> getFallbackRecommendations(String itemType, int limit) {
        List<RecommendationVO> fallbackList = new ArrayList<>();
        if (UserBehavior.ItemType.CONTEST.getCode().equals(itemType)) {
            // 获取最新竞赛作为降级方案（原viewCount字段不存在，暂用createTime）
            List<Contest> hotContests = contestMapper.selectList(new QueryWrapper<Contest>()
                    .orderByDesc("createTime")
                    .last("LIMIT " + limit));
            for (Contest contest : hotContests) {
                RecommendationVO vo = new RecommendationVO();
                vo.setItemId(contest.getId());
                vo.setItemName(contest.getContestName());
                vo.setScore(BigDecimal.valueOf(0.5)); // 默认分数
                vo.setAlgorithm(RecommendationResult.Algorithm.FALLBACK.getCode());
                vo.setReason("热门竞赛推荐"); // 实际上是最新
                vo.setItemDetail(contestService.getContestVO(contest));
                fallbackList.add(vo);
            }
        } else if (UserBehavior.ItemType.JOB.getCode().equals(itemType)) {
            // 获取最新职业作为降级方案（原viewCount字段不存在，暂用createTime）
            List<JobInfo> hotJobs = jobInfoMapper.selectList(new QueryWrapper<JobInfo>()
                    .orderByDesc("createTime")
                    .last("LIMIT " + limit));
            for (JobInfo job : hotJobs) {
                RecommendationVO vo = new RecommendationVO();
                vo.setItemId(job.getId());
                vo.setItemName(job.getWorkName());
                vo.setScore(BigDecimal.valueOf(0.5)); // 默认分数
                vo.setAlgorithm(RecommendationResult.Algorithm.FALLBACK.getCode());
                vo.setReason("热门职业推荐"); // 实际上是最新
                vo.setItemDetail(job);
                fallbackList.add(vo);
            }
        }
        log.info("执行降级推荐: itemType={}, 推荐数量={}", itemType, fallbackList.size());
        return fallbackList;
    }


    @Override
    public void refreshRecommendationCache(Long userId, String itemType) {
        String cacheKey = RECOMMENDATION_CACHE_PREFIX + userId + ":" + itemType;
        redisTemplate.delete(cacheKey);
        log.info("清除推荐缓存: userId={}, itemType={}", userId, itemType);
    }
}
