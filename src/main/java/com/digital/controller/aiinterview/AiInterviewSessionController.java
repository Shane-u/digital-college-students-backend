package com.digital.controller.aiinterview;

import com.digital.common.BaseResponse;
import com.digital.common.ErrorCode;
import com.digital.common.ResultUtils;
import com.digital.exception.ThrowUtils;
import com.digital.model.dto.aiinterview.AudioAnswerUploadRequest;
import com.digital.model.dto.aiinterview.TextAnswerUploadRequest;
import com.digital.model.dto.aiinterview.CreateInterviewSessionRequest;
import com.digital.model.dto.aiinterview.NextQuestionRequest;
import com.digital.model.entity.User;
import com.digital.model.vo.aiinterview.AnswerVO;
import com.digital.model.vo.aiinterview.InterviewReportVO;
import com.digital.model.vo.aiinterview.InterviewSessionVO;
import com.digital.model.vo.aiinterview.QuestionVO;
import com.digital.service.UserService;
import com.digital.service.aiinterview.InterviewSessionService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/ai-interview/sessions")
@Slf4j
public class AiInterviewSessionController {

    @Resource
    private InterviewSessionService sessionService;

    @Resource
    private UserService userService;

    @PostMapping("")
    public BaseResponse<InterviewSessionVO> create(@RequestBody CreateInterviewSessionRequest body,
                                                  @RequestParam(value = "userId", required = false) Long userId,
                                                  HttpServletRequest request) {
        Long resolvedUserId = resolveUserId(request, userId);
        InterviewSessionVO vo = sessionService.createSession(
                resolvedUserId,
                body.getResumeId(),
                body.getInterviewType(),
                body.getLanguage(),
                body.getDifficulty(),
                body.getPersona(),
                body.getDurationMinutes(),
                body.getEnableCoding(),
                body.getEnableRealtimeHints()
        );
        return ResultUtils.success(vo);
    }

    @PostMapping("/{sessionId}/next-question")
    public BaseResponse<QuestionVO> nextQuestion(@PathVariable Long sessionId,
                                                 @RequestBody(required = false) NextQuestionRequest body,
                                                 @RequestParam(value = "userId", required = false) Long userId,
                                                 HttpServletRequest request) {
        Long resolvedUserId = resolveUserId(request, userId);
        boolean needTtsAudio = body != null && Boolean.TRUE.equals(body.getNeedTtsAudio());
        return ResultUtils.success(sessionService.nextQuestion(resolvedUserId, sessionId, needTtsAudio));
    }

    @PostMapping(value = "/{sessionId}/answers/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<AnswerVO> uploadAudio(@PathVariable Long sessionId,
                                              @RequestPart("audioFile") MultipartFile audioFile,
                                              AudioAnswerUploadRequest body,
                                              @RequestParam(value = "userId", required = false) Long userId,
                                              HttpServletRequest request) {
        Long resolvedUserId = resolveUserId(request, userId);
        return ResultUtils.success(sessionService.uploadAudioAnswer(
                resolvedUserId,
                sessionId,
                body == null ? null : body.getQuestionId(),
                body == null ? null : body.getDurationSeconds(),
                audioFile
        ));
    }

    /**
     * 文本回答（供实时语音 / Go 服务调用）
     */
    @PostMapping("/{sessionId}/answers/text")
    public BaseResponse<AnswerVO> uploadText(@PathVariable Long sessionId,
                                             @RequestBody TextAnswerUploadRequest body,
                                             @RequestParam(value = "userId", required = false) Long userId,
                                             HttpServletRequest request) {
        Long resolvedUserId = resolveUserId(request, userId);
        ThrowUtils.throwIf(body == null || body.getQuestionId() == null,
                ErrorCode.PARAMS_ERROR, "questionId 不能为空");
        return ResultUtils.success(sessionService.uploadTextAnswer(
                resolvedUserId,
                sessionId,
                body.getQuestionId(),
                body.getDurationSeconds(),
                body.getTextAnswer(),
                body.getAsrConfidence()
        ));
    }

    @PostMapping("/{sessionId}/finish")
    public BaseResponse<InterviewReportVO> finish(@PathVariable Long sessionId,
                                                 @RequestParam(value = "userId", required = false) Long userId,
                                                 HttpServletRequest request) {
        Long resolvedUserId = resolveUserId(request, userId);
        return ResultUtils.success(sessionService.finish(resolvedUserId, sessionId));
    }

    @GetMapping("/{sessionId}/report")
    public BaseResponse<InterviewReportVO> report(@PathVariable Long sessionId,
                                                 @RequestParam(value = "userId", required = false) Long userId,
                                                 HttpServletRequest request) {
        Long resolvedUserId = resolveUserId(request, userId);
        return ResultUtils.success(sessionService.getReport(resolvedUserId, sessionId));
    }

    private Long resolveUserId(HttpServletRequest request, Long userId) {
        Long resolved = null;
        try {
            User loginUser = userService.getLoginUser(request);
            if (loginUser != null) {
                resolved = loginUser.getId();
            }
        } catch (Exception ignored) {
        }
        if (resolved == null) {
            resolved = userId;
        }
        ThrowUtils.throwIf(resolved == null, ErrorCode.NOT_LOGIN_ERROR, "缺少用户身份，请先登录或携带 userId");
        return resolved;
    }
}

