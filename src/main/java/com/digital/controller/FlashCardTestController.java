package com.digital.controller;

import com.digital.common.BaseResponse;
import com.digital.common.ErrorCode;
import com.digital.common.ResultUtils;
import com.digital.exception.BusinessException;
import com.digital.model.dto.flashcard.FlashCardTestGenerateRequest;
import com.digital.model.dto.flashcard.FlashCardTestSubmitRequest;
import com.digital.model.entity.User;
import com.digital.model.vo.FlashCardTestAttemptSummaryVO;
import com.digital.model.vo.FlashCardTestPaperSummaryVO;
import com.digital.model.vo.FlashCardTestResultVO;
import com.digital.model.vo.FlashCardTestQuestionResultVO;
import com.digital.model.vo.FlashCardTestVO;
import com.digital.service.FlashCardTestService;
import com.digital.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

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

    /**
     * 列出某个闪卡节点下的所有历史试卷（一次 generate 生成一套题）
     */
    @GetMapping("/papers")
    public BaseResponse<List<FlashCardTestPaperSummaryVO>> listPapers(@RequestParam String nodeId,
                                                                      @RequestParam(required = false) String difficulty,
                                                                      HttpServletRequest httpServletRequest) {
        if (StringUtils.isBlank(nodeId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "nodeId 不能为空");
        }
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(flashCardTestService.listPapers(loginUser.getId(), nodeId, difficulty));
    }

    /**
     * 获取某套试卷的题目（每题得分取历史最高分）
     */
    @GetMapping("/{testId}/questions/best")
    public BaseResponse<List<FlashCardTestQuestionResultVO>> getQuestionsWithBest(@PathVariable Long testId,
                                                                                  HttpServletRequest httpServletRequest) {
        if (testId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "testId 不能为空");
        }
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(flashCardTestService.getPaperQuestionsWithBestScore(loginUser.getId(), testId));
    }

    /**
     * 获取某套试卷的提交历史（每次 submit 一条记录）
     */
    @GetMapping("/{testId}/attempts")
    public BaseResponse<List<FlashCardTestAttemptSummaryVO>> listAttempts(@PathVariable Long testId,
                                                                          HttpServletRequest httpServletRequest) {
        if (testId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "testId 不能为空");
        }
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(flashCardTestService.listAttempts(loginUser.getId(), testId));
    }

    /**
     * 获取某次提交（attempt）的逐题批改明细快照（可回放当次作答）
     */
    @GetMapping("/attempts/{attemptId}")
    public BaseResponse<List<FlashCardTestQuestionResultVO>> getAttemptDetail(@PathVariable Long attemptId,
                                                                              HttpServletRequest httpServletRequest) {
        if (attemptId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "attemptId 不能为空");
        }
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(flashCardTestService.getAttemptDetail(loginUser.getId(), attemptId));
    }

    /**
     * 加载历史试卷题目（不带历史作答/得分），用于重做
     */
    @GetMapping("/papers/{testId}")
    public BaseResponse<FlashCardTestVO> loadPaper(@PathVariable Long testId,
                                                   HttpServletRequest httpServletRequest) {
        if (testId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "testId 不能为空");
        }
        User loginUser = userService.getLoginUser(httpServletRequest);
        FlashCardTestVO vo = flashCardTestService.loadPaper(loginUser.getId(), testId);
        if (vo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "试卷不存在");
        }
        return ResultUtils.success(vo);
    }
}

