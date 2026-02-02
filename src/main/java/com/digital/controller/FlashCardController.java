package com.digital.controller;

import com.digital.common.BaseResponse;
import com.digital.common.DeleteRequest;
import com.digital.common.ErrorCode;
import com.digital.common.ResultUtils;
import com.digital.exception.BusinessException;
import com.digital.model.dto.flashcard.FlashCardAIAssistRequest;
import com.digital.model.dto.flashcard.FlashCardConfirmRequest;
import com.digital.model.dto.flashcard.FlashCardGenerateRequest;
import com.digital.model.dto.flashcard.FlashCardReviewRequest;
import com.digital.model.dto.flashcard.FlashCardUpdateRequest;
import com.digital.model.entity.User;
import com.digital.model.vo.FlashCardVO;
import com.digital.model.vo.FlashCardProgressVO; // 新增导入
import com.digital.manager.FlashCardProgressManager; // 新增导入
import com.digital.model.dto.flashcard.DeleteHierarchyRequest; // 新增导入
import com.digital.service.FlashCardService;
import com.digital.service.Neo4jFlashCardService;
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

    @Resource
    private FlashCardProgressManager flashCardProgressManager; // 新增

    @Resource
    private Neo4jFlashCardService neo4jFlashCardService;

    /**
     * 生成闪卡（异步）
     * 立即返回，后台异步生成内容
     */
    @PostMapping("/generate")
    public BaseResponse<String> generateFlashCard(@RequestBody FlashCardGenerateRequest request,
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
        if (request == null || StringUtils.isBlank(request.getId())) {
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
        if (request == null || StringUtils.isBlank(request.getId()) || request.getDifficultyLevel() == null) {
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
        if (request == null || StringUtils.isBlank(request.getId()) || StringUtils.isBlank(request.getPrompt())) {
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
    public BaseResponse<String> getFlashCardStatus(@org.springframework.web.bind.annotation.RequestParam String flashCardId,
                                                     HttpServletRequest httpServletRequest) {
        if (flashCardId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "闪卡ID不能为空");
        }

        userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(flashCardService.getFlashCardStatus(flashCardId));
    }

    /**
     * 查询闪卡生成进度
     * @param flashCardId 闪卡ID
     * @param httpServletRequest HttpServletRequest
     * @return 闪卡生成进度信息
     */
    @GetMapping("/progress")
    public BaseResponse<FlashCardProgressVO> getFlashCardProgress(@org.springframework.web.bind.annotation.RequestParam String flashCardId,
                                                                HttpServletRequest httpServletRequest) {
        if (flashCardId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "闪卡ID不能为空");
        }
        User loginUser = userService.getLoginUser(httpServletRequest); // 确保用户已登录
        FlashCardProgressVO progressVO = flashCardProgressManager.getProgress(flashCardId);
        // 验证用户权限，确保只能查询自己的闪卡进度
        if (!"NOT_FOUND".equals(progressVO.getStatus()) && !loginUser.getId().equals(progressVO.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权限查询该闪卡进度");
        }
        return ResultUtils.success(progressVO);
    }

    /**
     * 获取用户的暂存闪卡列表
     */
    @GetMapping("/temp-list")
    public BaseResponse<List<FlashCardVO>> getTempUserFlashCards(HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(flashCardService.getTempUserFlashCards(loginUser.getId()));
    }

    /**
     * 确认保存闪卡到终库
     * @param request 包含临时闪卡ID和层级标签路径的请求体
     * @param httpServletRequest HttpServletRequest
     * @return 确认结果
     */
    @PostMapping("/confirm")
    public BaseResponse<Boolean> confirmFlashCard(@RequestBody FlashCardConfirmRequest request,
                                                  HttpServletRequest httpServletRequest) {
        if (request == null || StringUtils.isBlank(request.getId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "闪卡ID不能为空");
        }
        if (StringUtils.isBlank(request.getHierarchyPath())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "层级标签路径不能为空");
        }
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(flashCardService.confirmFlashCard(loginUser.getId(), request.getId(), request.getHierarchyPath()));
    }

    /**
     * 删除临时闪卡及其进度
     * @param request 包含临时闪卡ID的请求体
     * @param httpServletRequest HttpServletRequest
     * @return 删除结果
     */
    @PostMapping("/temp/delete")
    public BaseResponse<Boolean> deleteTempFlashCard(@RequestBody DeleteRequest request,
                                                               HttpServletRequest httpServletRequest) {
        if (request == null || StringUtils.isBlank(request.getId())) {
             throw new BusinessException(ErrorCode.PARAMS_ERROR, "闪卡ID不能为空");
        }
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(flashCardService.deleteTempFlashCard(loginUser.getId(), request.getId()));
    }

    /**
     * 更新暂存闪卡
     */
    @PostMapping("/temp/update")
    public BaseResponse<Boolean> updateTempFlashCard(@RequestBody FlashCardUpdateRequest request,
                                                     HttpServletRequest httpServletRequest) {
        if (request == null || StringUtils.isBlank(request.getId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "闪卡ID不能为空");
        }
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(flashCardService.updateTempFlashCard(loginUser.getId(), request));
    }

    /**
     * 查看临时闪卡详情（用于预览）
     *
     * @param tempFlashCardId 临时闪卡ID
     * @param httpServletRequest HttpServletRequest
     * @return 临时闪卡详情
     */
    @GetMapping("/temp")
    public BaseResponse<FlashCardVO> getTempFlashCard(@org.springframework.web.bind.annotation.RequestParam("id") String tempFlashCardId,
                                                      HttpServletRequest httpServletRequest) {
        if (StringUtils.isBlank(tempFlashCardId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "闪卡ID不能为空");
        }
        User loginUser = userService.getLoginUser(httpServletRequest);
        // 复用 getFlashCardByIdString 逻辑，不过需要封装成 VO
        com.digital.model.entity.FlashCard flashCard = flashCardService.getFlashCardByIdString(tempFlashCardId);

        if (flashCard == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "临时闪卡不存在或已过期");
        }

        if (!flashCard.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权限查看该闪卡");
        }

        return ResultUtils.success(flashCardService.getFlashCardVO(flashCard));
    }

    @GetMapping("/temp/{id}")
    public BaseResponse<FlashCardVO> getTempFlashCardPathVariable(@org.springframework.web.bind.annotation.PathVariable("id") String tempFlashCardId,
                                                      HttpServletRequest httpServletRequest) {
        return getTempFlashCard(tempFlashCardId, httpServletRequest);
    }

    /**
     * 删除指定闪卡层级及其所有关联内容
     *
     * @param request 包含层级路径的请求体
     * @param httpServletRequest HttpServletRequest
     * @return 删除结果
     */
    @PostMapping("/delete-hierarchy")
    public BaseResponse<Boolean> deleteFlashCardHierarchy(@RequestBody com.digital.model.dto.flashcard.DeleteHierarchyRequest request,
                                                          HttpServletRequest httpServletRequest) {
        if (request == null || StringUtils.isBlank(request.getHierarchyPath())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "层级标签路径不能为空");
        }
        User loginUser = userService.getLoginUser(httpServletRequest);
        flashCardService.deleteFlashCardHierarchy(loginUser.getId(), request.getHierarchyPath());
        return ResultUtils.success(true);
    }
}
