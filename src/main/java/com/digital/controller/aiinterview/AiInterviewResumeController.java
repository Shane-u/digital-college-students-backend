package com.digital.controller.aiinterview;

import com.digital.common.BaseResponse;
import com.digital.common.ErrorCode;
import com.digital.common.ResultUtils;
import com.digital.exception.ThrowUtils;
import com.digital.model.dto.aiinterview.ResumeAnalysisRequest;
import com.digital.model.dto.aiinterview.ResumeUploadRequest;
import com.digital.model.entity.User;
import com.digital.model.vo.aiinterview.ResumeAnalysisVO;
import com.digital.model.vo.aiinterview.ResumeUploadVO;
import com.digital.model.vo.aiinterview.ResumeVO;
import com.digital.service.UserService;
import com.digital.service.aiinterview.ResumeAnalysisService;
import com.digital.service.aiinterview.ResumeService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/ai-interview/resumes")
@Slf4j
public class AiInterviewResumeController {

    @Resource
    private ResumeService resumeService;

    @Resource
    private ResumeAnalysisService resumeAnalysisService;

    @Resource
    private UserService userService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<ResumeUploadVO> upload(@RequestPart("file") MultipartFile file,
                                              ResumeUploadRequest uploadRequest,
                                              @RequestParam(value = "userId", required = false) Long userId,
                                              HttpServletRequest request) {
        Long resolvedUserId = resolveUserId(request, userId);
        ResumeUploadVO vo = resumeService.uploadResume(resolvedUserId, file);
        return ResultUtils.success(vo);
    }

    @GetMapping("/{resumeId}")
    public BaseResponse<ResumeVO> get(@PathVariable Long resumeId,
                                     @RequestParam(value = "userId", required = false) Long userId,
                                     HttpServletRequest request) {
        Long resolvedUserId = resolveUserId(request, userId);
        return ResultUtils.success(resumeService.getResume(resolvedUserId, resumeId));
    }

    /**
     * 列出当前用户已上传的简历
     */
    @GetMapping("/list")
    public BaseResponse<java.util.List<ResumeVO>> list(@RequestParam(value = "userId", required = false) Long userId,
                                                       HttpServletRequest request) {
        Long resolvedUserId = resolveUserId(request, userId);
        return ResultUtils.success(resumeService.listResumes(resolvedUserId));
    }

    @PostMapping("/{resumeId}/analysis")
    public BaseResponse<ResumeAnalysisVO> analyze(@PathVariable Long resumeId,
                                                 @RequestBody(required = false) ResumeAnalysisRequest body,
                                                 @RequestParam(value = "userId", required = false) Long userId,
                                                 HttpServletRequest request) {
        Long resolvedUserId = resolveUserId(request, userId);
        String targetRole = body == null ? null : body.getTargetRole();
        String targetLevel = body == null ? null : body.getTargetLevel();
        return ResultUtils.success(resumeAnalysisService.analyze(resolvedUserId, resumeId, targetRole, targetLevel));
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

