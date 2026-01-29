package com.digital.manager;

import com.digital.model.vo.FlashCardProgressVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisTemplate;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;

import java.util.Date;

@Component
@Slf4j
public class FlashCardProgressManager {

    // 使用ConcurrentHashMap存储每个闪卡ID的进度信息
    // 确保线程安全，并以闪卡ID作为键
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${flashcard.temp.expiration-days:7}") // 默认7天
    private long tempFlashCardExpirationDays;

    private static final String FLASHCARD_PROGRESS_PREFIX = "flashcard_progress:";

    /**
     * 初始化闪卡生成进度
     * @param flashCardId 闪卡ID
     * @param userId 用户ID
     */
    public void initProgress(String flashCardId, Long userId) {
        FlashCardProgressVO progressVO = new FlashCardProgressVO();
        progressVO.setFlashCardId(flashCardId); // 直接使用String类型的flashCardId
        progressVO.setUserId(userId);
        progressVO.setStatus("INITIALIZING");
        progressVO.setProgress(0);
        progressVO.setMessage("闪卡生成任务已初始化");
        progressVO.setStartTime(new Date());
        redisTemplate.opsForValue().set(FLASHCARD_PROGRESS_PREFIX + flashCardId, progressVO, tempFlashCardExpirationDays, java.util.concurrent.TimeUnit.DAYS); // 默认7天过期，稍后从配置中读取
        log.info("初始化闪卡生成进度：flashCardId={}, userId={}", flashCardId, userId);
    }

    /**
     * 更新闪卡生成进度
     * @param flashCardId 闪卡ID
     * @param status 状态
     * @param progress 百分比进度
     * @param message 消息
     */
    public void updateProgress(String flashCardId, String status, Integer progress, String message) {
        FlashCardProgressVO progressVO = (FlashCardProgressVO) redisTemplate.opsForValue().get(FLASHCARD_PROGRESS_PREFIX + flashCardId);
        if (progressVO != null) {
            progressVO.setStatus(status);
            progressVO.setProgress(progress);
            progressVO.setMessage(message);
            if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
                progressVO.setEndTime(new Date());
            }
            redisTemplate.opsForValue().set(FLASHCARD_PROGRESS_PREFIX + flashCardId, progressVO, tempFlashCardExpirationDays, java.util.concurrent.TimeUnit.DAYS); // 更新后重新设置过期时间
            log.info("更新闪卡生成进度：flashCardId={}, status={}, progress={}, message={}", flashCardId, status, progress, message);
        } else {
            log.warn("尝试更新不存在的闪卡进度：flashCardId={}", flashCardId);
        }
    }

    /**
     * 获取闪卡生成进度
     * @param flashCardId 闪卡ID
     * @return 进度信息
     */
    public FlashCardProgressVO getProgress(String flashCardId) {
        FlashCardProgressVO progressVO = (FlashCardProgressVO) redisTemplate.opsForValue().get(FLASHCARD_PROGRESS_PREFIX + flashCardId);
        if (progressVO == null) {
            log.warn("请求不存在的闪卡进度：flashCardId={}", flashCardId);
            // 如果进度不存在，返回一个默认的未找到状态
            FlashCardProgressVO notFound = new FlashCardProgressVO();
            notFound.setFlashCardId(flashCardId);
            notFound.setStatus("NOT_FOUND");
            notFound.setProgress(0);
            notFound.setMessage("指定闪卡ID的进度不存在或已过期");
            return notFound;
        }
        return progressVO;
    }

    /**
     * 移除闪卡生成进度 (例如，当任务完成或失败，并且前端已获取最终状态后可以移除)
     * @param flashCardId 闪卡ID
     */
    public void removeProgress(String flashCardId) {
        redisTemplate.delete(FLASHCARD_PROGRESS_PREFIX + flashCardId);
        log.info("移除闪卡生成进度：flashCardId={}", flashCardId);
    }
}
