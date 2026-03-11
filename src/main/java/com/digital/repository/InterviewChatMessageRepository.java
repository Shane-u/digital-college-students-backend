package com.digital.repository;

import com.digital.model.entity.InterviewChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewChatMessageRepository extends MongoRepository<InterviewChatMessage, String> {

    List<InterviewChatMessage> findBySessionIdOrderByCreateTimeAsc(String sessionId);
}
