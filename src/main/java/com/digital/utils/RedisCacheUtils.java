package com.digital.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存工具类
 *
 * @author Shane
 */
@Component
@Slf4j
public class RedisCacheUtils {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 缓存 key 前缀常量
     */
    public static class CacheKey {
        // 帖子相关
        public static final String POST_PREFIX = "post:";
        public static final String POST_LIST_PREFIX = "post:list:";
        public static final String POST_DETAIL_PREFIX = "post:detail:";
        
        // 竞赛相关
        public static final String CONTEST_LIST_PREFIX = "contest:list:";
        public static final String CONTEST_DETAIL_PREFIX = "contest:detail:";
        
        // 用户相关
        public static final String USER_PREFIX = "user:";
        
        // 题目相关
        public static final String QUESTION_PREFIX = "question:";
        
        // 推荐相关
        public static final String RECOMMENDATION_PREFIX = "recommendation:";
    }

    /**
     * 缓存过期时间常量（秒）
     */
    public static class ExpireTime {
        public static final long POST_LIST = 300;        // 帖子列表 5分钟
        public static final long POST_DETAIL = 600;      // 帖子详情 10分钟
        public static final long CONTEST_LIST = 600;     // 竞赛列表 10分钟
        public static final long CONTEST_DETAIL = 1800;  // 竞赛详情 30分钟（外部API）
        public static final long USER_INFO = 3600;       // 用户信息 1小时
        public static final long QUESTION = 3600;        // 题目 1小时
    }

    /**
     * 设置缓存
     *
     * @param key   缓存key
     * @param value 缓存值
     * @param time  过期时间（秒）
     */
    public void set(String key, Object value, long time) {
        try {
            redisTemplate.opsForValue().set(key, value, time, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("设置缓存失败，key: {}", key, e);
        }
    }

    /**
     * 设置缓存
     *
     * @param key   缓存key
     * @param value 缓存值
     */
    public void set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.error("设置缓存失败，key: {}", key, e);
        }
    }

    /**
     * 获取缓存
     *
     * @param key 缓存key
     * @return 缓存值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        try {
            return (T) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("获取缓存失败，key: {}", key, e);
            return null;
        }
    }

    /**
     * 删除缓存
     *
     * @param key 缓存key
     */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("删除缓存失败，key: {}", key, e);
        }
    }

    /**
     * 批量删除缓存（支持通配符）
     *
     * @param pattern 匹配模式，如 "post:*"
     */
    public void deleteByPattern(String pattern) {
        try {
            redisTemplate.delete(redisTemplate.keys(pattern));
        } catch (Exception e) {
            log.error("批量删除缓存失败，pattern: {}", pattern, e);
        }
    }

    /**
     * 判断缓存是否存在
     *
     * @param key 缓存key
     * @return 是否存在
     */
    public boolean exists(String key) {
        try {
            Boolean result = redisTemplate.hasKey(key);
            return result != null && result;
        } catch (Exception e) {
            log.error("判断缓存是否存在失败，key: {}", key, e);
            return false;
        }
    }

    /**
     * 设置过期时间
     *
     * @param key  缓存key
     * @param time 过期时间（秒）
     */
    public void expire(String key, long time) {
        try {
            redisTemplate.expire(key, time, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("设置过期时间失败，key: {}", key, e);
        }
    }
}
