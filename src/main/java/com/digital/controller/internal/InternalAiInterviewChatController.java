package com.digital.controller.internal;

import com.digital.common.BaseResponse;
import com.digital.common.ErrorCode;
import com.digital.common.ResultUtils;
import com.digital.exception.ThrowUtils;
import com.digital.model.dto.aiinterview.InterviewChatAppendRequest;
import com.digital.service.aiinterview.InterviewChatService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint for Go voice service to append AI interview chat (e.g. assistant reply) to MongoDB.
 */
@RestController
@RequestMapping("/internal/ai-interview/sessions")
@Slf4j
public class InternalAiInterviewChatController {

    @Resource
    private InterviewChatService interviewChatService;

    @Value("${internal.auth.token:}")
    private String internalAuthToken;

    @PostMapping("/{sessionId}/chat")
    public BaseResponse<Void> appendChat(
            @PathVariable Long sessionId,
            @RequestBody InterviewChatAppendRequest body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token
    ) {
        if (StringUtils.isBlank(internalAuthToken)) {
            return new BaseResponse<>(ErrorCode.FORBIDDEN_ERROR.getCode(), null, "internal auth disabled");
        }
        if (!internalAuthToken.equals(token)) {
            return new BaseResponse<>(ErrorCode.NO_AUTH_ERROR.getCode(), null, "invalid internal token");
        }
        ThrowUtils.throwIf(body == null, ErrorCode.PARAMS_ERROR, "body 不能为空");
        ThrowUtils.throwIf(body.getUserId() == null, ErrorCode.PARAMS_ERROR, "userId 不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(body.getRole()), ErrorCode.PARAMS_ERROR, "role 不能为空");

        interviewChatService.appendMessage(
                sessionId,
                body.getUserId(),
                body.getRole(),
                StringUtils.defaultString(body.getContent()),
                null,
                null
        );
        return ResultUtils.success(null);
    }
}
