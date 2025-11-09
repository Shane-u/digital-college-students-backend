package com.digital.service.impl;

import com.digital.common.ErrorCode;
import com.digital.exception.BusinessException;
import com.digital.exception.ThrowUtils;
import com.digital.manager.DoubaoManager;
import com.digital.model.dto.chat.ChatRequest;
import com.digital.model.dto.chat.ChatResponse;
import com.digital.model.dto.chat.ChatSessionRequest;
import com.digital.model.dto.chat.Message;
import com.digital.model.dto.chat.StreamChatResponse;
import com.digital.model.entity.ChatMessage;
import com.digital.model.entity.ChatSession;
import com.digital.model.vo.ChatMessageVO;
import com.digital.model.vo.ChatSessionVO;
import com.digital.repository.ChatMessageRepository;
import com.digital.repository.ChatSessionRepository;
import com.digital.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 聊天服务实现类
 * 实现聊天功能的业务逻辑，包括会话管理、历史记录、消息保存等
 *
 * @author Shane
 */
@Service
@Slf4j
public class ChatServiceImpl implements ChatService {

    @Resource
    private DoubaoManager doubaoManager;

    @Resource
    private ChatSessionRepository chatSessionRepository;

    @Resource
    private ChatMessageRepository chatMessageRepository;

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        // 参数校验
        validateChatRequest(chatRequest);

        // 获取或创建会话
        String sessionId = getOrCreateSession(chatRequest);

        // 构建完整的消息列表（包含历史记录）
        List<Message> fullMessages = buildMessageList(chatRequest, sessionId);

        // 处理多模态消息
        List<Message> processedMessages = processMultimodalMessages(fullMessages);

        // 创建新的请求对象（包含历史消息）
        ChatRequest fullRequest = new ChatRequest();
        BeanUtils.copyProperties(chatRequest, fullRequest);
        fullRequest.setMessages(processedMessages);
        fullRequest.setSessionId(sessionId);

        // 保存用户消息
        saveUserMessage(chatRequest, sessionId);

        // 调用Manager层执行聊天请求
        ChatResponse response = doubaoManager.chat(fullRequest);

        // 保存AI回复
        saveAssistantMessage(response, sessionId, chatRequest.getUserId());

        // 更新会话时间
        updateSessionTime(sessionId);

        return response;
    }

    @Override
    public void streamChat(ChatRequest chatRequest, Consumer<StreamChatResponse> onChunk) {
        // 参数校验
        validateChatRequest(chatRequest);

        // 获取或创建会话
        String sessionId = getOrCreateSession(chatRequest);

        // 构建完整的消息列表（包含历史记录）
        List<Message> fullMessages = buildMessageList(chatRequest, sessionId);

        // 处理多模态消息
        List<Message> processedMessages = processMultimodalMessages(fullMessages);

        // 创建新的请求对象（包含历史消息）
        ChatRequest fullRequest = new ChatRequest();
        BeanUtils.copyProperties(chatRequest, fullRequest);
        fullRequest.setMessages(processedMessages);
        fullRequest.setSessionId(sessionId);

        // 保存用户消息
        saveUserMessage(chatRequest, sessionId);

        // 用于收集流式响应内容
        StringBuilder responseContent = new StringBuilder();

        // 调用Manager层执行流式聊天请求
        doubaoManager.streamChat(fullRequest, streamResponse -> {
            // 收集内容
            String deltaContent = streamResponse.getDeltaContent();
            if (deltaContent != null) {
                responseContent.append(deltaContent);
            }

            // 调用回调
            onChunk.accept(streamResponse);

            // 如果流结束，保存AI回复
            if (streamResponse.isFinished()) {
                saveAssistantMessage(responseContent.toString(), sessionId, chatRequest.getUserId());
                updateSessionTime(sessionId);
            }
        });
    }

    /**
     * 获取或创建会话
     */
    private String getOrCreateSession(ChatRequest chatRequest) {
        Long userId = chatRequest.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");

        String sessionId = chatRequest.getSessionId();
        if (StringUtils.isNotBlank(sessionId)) {
            // 验证会话是否存在且属于该用户
            ChatSession session = chatSessionRepository.findByIdAndUserId(sessionId, userId);
            ThrowUtils.throwIf(session == null || session.getIsDelete(), 
                    ErrorCode.NOT_FOUND_ERROR, "会话不存在");
            return sessionId;
        }

        // 创建新会话
        ChatSession session = new ChatSession();
        session.setId(UUID.randomUUID().toString());
        session.setUserId(userId);
        
        // 生成标题（使用第一条用户消息的前20个字符）
        String title = generateSessionTitle(chatRequest);
        session.setTitle(title);
        
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        session.setIsDelete(false);
        
        chatSessionRepository.save(session);
        log.info("创建新会话: sessionId={}, userId={}, title={}", session.getId(), userId, title);
        
        return session.getId();
    }

    /**
     * 生成会话标题
     */
    private String generateSessionTitle(ChatRequest chatRequest) {
        if (chatRequest.getMessages() == null || chatRequest.getMessages().isEmpty()) {
            return "新对话";
        }
        
        // 找到第一条用户消息
        for (Message msg : chatRequest.getMessages()) {
            if ("user".equals(msg.getRole())) {
                String content = msg.getContent();
                if (StringUtils.isNotBlank(content)) {
                    return content.length() > 20 ? content.substring(0, 20) + "..." : content;
                }
                // 如果有图片或视频，使用特殊标题
                if (msg.hasMultimodalContent()) {
                    return "包含图片/视频的对话";
                }
            }
        }
        
        return "新对话";
    }

    /**
     * 构建完整的消息列表（包含历史记录）
     */
    private List<Message> buildMessageList(ChatRequest chatRequest, String sessionId) {
        List<Message> messages = new ArrayList<>();

        // 加载历史消息
        List<ChatMessage> historyMessages = chatMessageRepository
                .findBySessionIdAndUserIdAndIsDeleteOrderByCreateTimeAsc(
                        sessionId, chatRequest.getUserId(), false);

        // 转换历史消息为Message对象
        for (ChatMessage historyMsg : historyMessages) {
            Message msg = new Message();
            msg.setRole(historyMsg.getRole());
            msg.setContent(historyMsg.getContent());
            
            // 处理多模态内容
            if (historyMsg.getImageUrls() != null && !historyMsg.getImageUrls().isEmpty()) {
                msg.setImageUrls(historyMsg.getImageUrls());
            }
            if (historyMsg.getVideoUrls() != null && !historyMsg.getVideoUrls().isEmpty()) {
                msg.setVideoUrls(historyMsg.getVideoUrls());
            }
            
            messages.add(msg);
        }

        // 添加当前请求的消息
        if (chatRequest.getMessages() != null) {
            messages.addAll(chatRequest.getMessages());
        }

        return messages;
    }

    /**
     * 处理多模态消息，转换为API需要的格式
     */
    private List<Message> processMultimodalMessages(List<Message> messages) {
        return messages.stream().map(msg -> {
            // 如果消息包含多模态内容，需要转换为contentList格式
            if (msg.hasMultimodalContent()) {
                Message processedMsg = new Message();
                processedMsg.setRole(msg.getRole());
                
                List<Message.ContentItem> contentList = new ArrayList<>();
                
                // 添加文本内容
                if (StringUtils.isNotBlank(msg.getContent())) {
                    Message.ContentItem textItem = new Message.ContentItem();
                    textItem.setType("text");
                    textItem.setText(msg.getContent());
                    contentList.add(textItem);
                }
                
                // 添加图片
                if (msg.getImageUrls() != null && !msg.getImageUrls().isEmpty()) {
                    for (String imageUrl : msg.getImageUrls()) {
                        Message.ContentItem imageItem = new Message.ContentItem();
                        imageItem.setType("image_url");
                        Message.ImageUrl imageUrlObj = new Message.ImageUrl();
                        imageUrlObj.setUrl(imageUrl);
                        imageItem.setImageUrl(imageUrlObj);
                        contentList.add(imageItem);
                    }
                }
                
                // 添加视频
                if (msg.getVideoUrls() != null && !msg.getVideoUrls().isEmpty()) {
                    for (String videoUrl : msg.getVideoUrls()) {
                        Message.ContentItem videoItem = new Message.ContentItem();
                        videoItem.setType("video_url");
                        Message.VideoUrl videoUrlObj = new Message.VideoUrl();
                        videoUrlObj.setUrl(videoUrl);
                        videoItem.setVideoUrl(videoUrlObj);
                        contentList.add(videoItem);
                    }
                }
                
                processedMsg.setContentList(contentList);
                // 清空content字段，避免序列化冲突
                processedMsg.setContent(null);
                return processedMsg;
            }
            
            return msg;
        }).collect(Collectors.toList());
    }

    /**
     * 保存用户消息
     */
    private void saveUserMessage(ChatRequest chatRequest, String sessionId) {
        if (chatRequest.getMessages() == null || chatRequest.getMessages().isEmpty()) {
            return;
        }

        // 只保存用户消息
        for (Message msg : chatRequest.getMessages()) {
            if ("user".equals(msg.getRole())) {
                ChatMessage chatMessage = new ChatMessage();
                chatMessage.setId(UUID.randomUUID().toString());
                chatMessage.setSessionId(sessionId);
                chatMessage.setUserId(chatRequest.getUserId());
                chatMessage.setRole(msg.getRole());
                chatMessage.setContent(msg.getContent());
                chatMessage.setImageUrls(msg.getImageUrls());
                chatMessage.setVideoUrls(msg.getVideoUrls());
                chatMessage.setCreateTime(LocalDateTime.now());
                chatMessage.setIsDelete(false);
                
                chatMessageRepository.save(chatMessage);
            }
        }
    }

    /**
     * 保存AI回复（非流式）
     */
    private void saveAssistantMessage(ChatResponse response, String sessionId, Long userId) {
        String content = response.getContent();
        if (StringUtils.isBlank(content)) {
            return;
        }

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setId(UUID.randomUUID().toString());
        chatMessage.setSessionId(sessionId);
        chatMessage.setUserId(userId);
        chatMessage.setRole("assistant");
        chatMessage.setContent(content);
        chatMessage.setCreateTime(LocalDateTime.now());
        chatMessage.setIsDelete(false);
        
        chatMessageRepository.save(chatMessage);
    }

    /**
     * 保存AI回复（流式）
     */
    private void saveAssistantMessage(String content, String sessionId, Long userId) {
        if (StringUtils.isBlank(content)) {
            return;
        }

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setId(UUID.randomUUID().toString());
        chatMessage.setSessionId(sessionId);
        chatMessage.setUserId(userId);
        chatMessage.setRole("assistant");
        chatMessage.setContent(content);
        chatMessage.setCreateTime(LocalDateTime.now());
        chatMessage.setIsDelete(false);
        
        chatMessageRepository.save(chatMessage);
    }

    /**
     * 更新会话时间
     */
    private void updateSessionTime(String sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId).orElse(null);
        if (session != null) {
            session.setUpdateTime(LocalDateTime.now());
            chatSessionRepository.save(session);
        }
    }

    /**
     * 验证聊天请求参数
     */
    private void validateChatRequest(ChatRequest chatRequest) {
        ThrowUtils.throwIf(chatRequest == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");

        // 验证消息列表
        List<Message> messages = chatRequest.getMessages();
        ThrowUtils.throwIf(messages == null || messages.isEmpty(), 
                ErrorCode.PARAMS_ERROR, "消息列表不能为空");

        // 验证每条消息
        for (Message message : messages) {
            ThrowUtils.throwIf(message == null, ErrorCode.PARAMS_ERROR, "消息对象不能为空");
            ThrowUtils.throwIf(StringUtils.isBlank(message.getRole()), 
                    ErrorCode.PARAMS_ERROR, "消息角色不能为空");
            
            // 验证消息内容（文本或多模态）
            boolean hasContent = StringUtils.isNotBlank(message.getContent()) ||
                    (message.getImageUrls() != null && !message.getImageUrls().isEmpty()) ||
                    (message.getVideoUrls() != null && !message.getVideoUrls().isEmpty()) ||
                    (message.retrieveContentList() != null && !message.retrieveContentList().isEmpty());
            
            ThrowUtils.throwIf(!hasContent, 
                    ErrorCode.PARAMS_ERROR, "消息内容不能为空");

            // 验证角色值
            String role = message.getRole();
            if (!"system".equals(role) && !"user".equals(role) && !"assistant".equals(role)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, 
                        "消息角色必须是 system、user 或 assistant 之一");
            }
        }

        // 验证temperature参数（如果提供了）
        Double temperature = chatRequest.getTemperature();
        if (temperature != null) {
            ThrowUtils.throwIf(temperature < 0 || temperature > 2, 
                    ErrorCode.PARAMS_ERROR, "temperature参数必须在0-2之间");
        }

        // 验证topP参数（如果提供了）
        Double topP = chatRequest.getTopP();
        if (topP != null) {
            ThrowUtils.throwIf(topP < 0 || topP > 1, 
                    ErrorCode.PARAMS_ERROR, "topP参数必须在0-1之间");
        }
    }

    /**
     * 创建聊天会话
     */
    public ChatSessionVO createSession(ChatSessionRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        ThrowUtils.throwIf(request.getUserId() == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");

        ChatSession session = new ChatSession();
        session.setId(UUID.randomUUID().toString());
        session.setUserId(request.getUserId());
        session.setTitle(StringUtils.isNotBlank(request.getTitle()) ? 
                request.getTitle() : "新对话");
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        session.setIsDelete(false);

        chatSessionRepository.save(session);

        ChatSessionVO vo = new ChatSessionVO();
        BeanUtils.copyProperties(session, vo);
        return vo;
    }

    /**
     * 获取用户的会话列表
     */
    public List<ChatSessionVO> getSessions(Long userId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");

        List<ChatSession> sessions = chatSessionRepository
                .findByUserIdAndIsDeleteOrderByUpdateTimeDesc(userId, false);

        return sessions.stream().map(session -> {
            ChatSessionVO vo = new ChatSessionVO();
            BeanUtils.copyProperties(session, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 获取会话的消息列表
     */
    public List<ChatMessageVO> getMessages(String sessionId, Long userId) {
        ThrowUtils.throwIf(StringUtils.isBlank(sessionId), ErrorCode.PARAMS_ERROR, "会话ID不能为空");
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");

        // 验证会话是否属于该用户
        ChatSession session = chatSessionRepository.findByIdAndUserId(sessionId, userId);
        ThrowUtils.throwIf(session == null || session.getIsDelete(), 
                ErrorCode.NOT_FOUND_ERROR, "会话不存在");

        List<ChatMessage> messages = chatMessageRepository
                .findBySessionIdAndUserIdAndIsDeleteOrderByCreateTimeAsc(sessionId, userId, false);

        return messages.stream().map(msg -> {
            ChatMessageVO vo = new ChatMessageVO();
            BeanUtils.copyProperties(msg, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 删除会话（软删除）
     */
    public void deleteSession(String sessionId, Long userId) {
        ThrowUtils.throwIf(StringUtils.isBlank(sessionId), ErrorCode.PARAMS_ERROR, "会话ID不能为空");
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");

        ChatSession session = chatSessionRepository.findByIdAndUserId(sessionId, userId);
        ThrowUtils.throwIf(session == null || session.getIsDelete(), 
                ErrorCode.NOT_FOUND_ERROR, "会话不存在");

        session.setIsDelete(true);
        session.setUpdateTime(LocalDateTime.now());
        chatSessionRepository.save(session);

        // 软删除该会话的所有消息
        List<ChatMessage> messages = chatMessageRepository
                .findBySessionIdAndUserIdAndIsDeleteOrderByCreateTimeAsc(sessionId, userId, false);
        for (ChatMessage msg : messages) {
            msg.setIsDelete(true);
            chatMessageRepository.save(msg);
        }
    }
}
