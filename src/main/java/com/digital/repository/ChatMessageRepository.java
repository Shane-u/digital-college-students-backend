package com.digital.repository;

import com.digital.model.entity.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 聊天消息Repository
 *
 * @author Shane
 */
@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    /**
     * 根据会话ID查询所有未删除的消息，按创建时间正序
     *
     * @param sessionId 会话ID
     * @param isDelete 是否删除
     * @return 消息列表
     */
    List<ChatMessage> findBySessionIdAndIsDeleteOrderByCreateTimeAsc(String sessionId, Boolean isDelete);

    /**
     * 根据会话ID和用户ID查询所有未删除的消息，按创建时间正序
     *
     * @param sessionId 会话ID
     * @param userId 用户ID
     * @param isDelete 是否删除
     * @return 消息列表
     */
    List<ChatMessage> findBySessionIdAndUserIdAndIsDeleteOrderByCreateTimeAsc(String sessionId, Long userId, Boolean isDelete);

    /**
     * 根据会话ID删除所有消息（软删除）
     *
     * @param sessionId 会话ID
     */
    void deleteBySessionId(String sessionId);
}

