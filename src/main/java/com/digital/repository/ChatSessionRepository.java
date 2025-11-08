package com.digital.repository;

import com.digital.model.entity.ChatSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 聊天会话Repository
 *
 * @author Shane
 */
@Repository
public interface ChatSessionRepository extends MongoRepository<ChatSession, String> {

    /**
     * 根据用户ID查询所有未删除的会话，按更新时间倒序
     *
     * @param userId 用户ID
     * @param isDelete 是否删除
     * @return 会话列表
     */
    List<ChatSession> findByUserIdAndIsDeleteOrderByUpdateTimeDesc(Long userId, Boolean isDelete);

    /**
     * 根据会话ID和用户ID查询会话
     *
     * @param id 会话ID
     * @param userId 用户ID
     * @return 会话
     */
    ChatSession findByIdAndUserId(String id, Long userId);
}

