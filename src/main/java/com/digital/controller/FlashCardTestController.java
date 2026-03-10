package com.digital.controller;

import com.digital.common.BaseResponse;
import com.digital.common.ErrorCode;
import com.digital.common.ResultUtils;
import com.digital.exception.BusinessException;
import com.digital.model.dto.flashcard.FlashCardTestGenerateRequest;
import com.digital.model.dto.flashcard.FlashCardTestSubmitRequest;
import com.digital.model.entity.User;
import com.digital.model.vo.FlashCardTestResultVO;
import com.digital.model.vo.FlashCardTestVO;
import com.digital.service.FlashCardTestService;
import com.digital.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 闪卡 AI 测试接口
 *
 * 覆盖：出题 + 批改 + 点亮 + 成长同步
 */
@RestController
@RequestMapping("/flash-card/test")
@Slf4j
public class FlashCardTestController {

    @Resource
    private FlashCardTestService flashCardTestService;

    @Resource
    private UserService userService;

    /**
     * 生成测试题
     */
    @PostMapping("/generate")
    public BaseResponse<FlashCardTestVO> generate(@RequestBody FlashCardTestGenerateRequest request,
                                                  HttpServletRequest httpServletRequest) {
        if (request == null || StringUtils.isBlank(request.getNodeId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "节点ID不能为空");
        }
        User loginUser = userService.getLoginUser(httpServletRequest);
        FlashCardTestVO vo = flashCardTestService.generateTest(loginUser.getId(), request);
        return ResultUtils.success(vo);
    }

    /**
     * 提交并批改测试
     */
    @PostMapping("/submit")
    public BaseResponse<FlashCardTestResultVO> submit(@RequestBody FlashCardTestSubmitRequest request,
                                                      HttpServletRequest httpServletRequest) {
        if (request == null || request.getTestId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "测试ID不能为空");
        }
        User loginUser = userService.getLoginUser(httpServletRequest);
        FlashCardTestResultVO vo = flashCardTestService.submitAndCorrect(loginUser.getId(), request);
        return ResultUtils.success(vo);
    }
}

