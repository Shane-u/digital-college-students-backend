package com.digital.service;

/**
 * 学习路径点亮回溯服务
 *
 * 规则：
 * - 叶子节点：关联的闪卡全部点亮 => 叶子点亮
 * - 非叶子节点：直接子学习节点全部点亮 => 点亮
 */
public interface LearningPathLightingService {

    /**
     * 在某条路径内全量重算（适合 match 结果更新后调用）
     */
    void recomputePath(Long userId, String pathId);

    /**
     * 某个闪卡点亮状态变化后，重算所有受影响的学习路径
     */
    void recomputeByFlashcard(Long userId, String flashcardId);
}

