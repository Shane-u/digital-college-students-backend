package com.digital.controller;

import com.digital.common.BaseResponse;
import com.digital.common.DeleteRequest;
import com.digital.common.ErrorCode;
import com.digital.common.ResultUtils;
import com.digital.exception.BusinessException;
import com.digital.model.dto.flashcard.FlashCardAIAssistRequest;
import com.digital.model.dto.flashcard.FlashCardGenerateRequest;
import com.digital.model.dto.flashcard.FlashCardReviewRequest;
import com.digital.model.dto.flashcard.FlashCardUpdateRequest;
import com.digital.model.entity.User;
import com.digital.model.vo.FlashCardVO;
import com.digital.service.FlashCardService;
import com.digital.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 记忆闪卡接口
 */
@RestController
@RequestMapping("/flash-card")
@Slf4j
public class FlashCardController {

    @Resource
    private FlashCardService flashCardService;

    @Resource
    private UserService userService;

    /**
     * 生成闪卡（异步）
     * 立即返回，后台异步生成内容
     */
    @PostMapping("/generate")
    public BaseResponse<Long> generateFlashCard(@RequestBody FlashCardGenerateRequest request,
                                                 HttpServletRequest httpServletRequest) {
        if (request == null || StringUtils.isBlank(request.getOriginalContent())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(flashCardService.generateFlashCard(loginUser.getId(), request));
    }

    /**
     * 获取用户的闪卡列表
     */
    @GetMapping("/list")
    public BaseResponse<List<FlashCardVO>> getUserFlashCards(HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(flashCardService.getUserFlashCards(loginUser.getId()));
    }

    /**
     * 获取需要复习的闪卡列表
     */
    @GetMapping("/review-list")
    public BaseResponse<List<FlashCardVO>> getReviewFlashCards(HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(flashCardService.getReviewFlashCards(loginUser.getId()));
    }

    /**
     * 更新闪卡
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateFlashCard(@RequestBody FlashCardUpdateRequest request,
                                                  HttpServletRequest httpServletRequest) {
        if (request == null || request.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(flashCardService.updateFlashCard(loginUser.getId(), request));
    }

    /**
     * 删除闪卡
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteFlashCard(@RequestBody DeleteRequest deleteRequest,
                                                   HttpServletRequest httpServletRequest) {
        if (deleteRequest == null || deleteRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(flashCardService.deleteFlashCard(loginUser.getId(), deleteRequest.getId()));
    }

    /**
     * 复习闪卡
     */
    @PostMapping("/review")
    public BaseResponse<Boolean> reviewFlashCard(@RequestBody FlashCardReviewRequest request,
                                                  HttpServletRequest httpServletRequest) {
        if (request == null || request.getId() == null || request.getDifficultyLevel() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(flashCardService.reviewFlashCard(loginUser.getId(), request));
    }

    /**
     * AI辅助修改闪卡
     */
    @PostMapping("/ai-assist")
    public BaseResponse<FlashCardVO> aiAssistFlashCard(@RequestBody FlashCardAIAssistRequest request,
                                                        HttpServletRequest httpServletRequest) {
        if (request == null || request.getId() == null || StringUtils.isBlank(request.getPrompt())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(flashCardService.aiAssistFlashCard(loginUser.getId(), request));
    }

    /**
     * 查询闪卡生成状态
     * 返回状态：generating-生成中, success-成功, failed-失败, not_found-不存在
     */
    @GetMapping("/status")
    public BaseResponse<String> getFlashCardStatus(@org.springframework.web.bind.annotation.RequestParam Long flashCardId,
                                                     HttpServletRequest httpServletRequest) {
        if (flashCardId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "闪卡ID不能为空");
        }

        userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(flashCardService.getFlashCardStatus(flashCardId));
    }
}

