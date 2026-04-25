package com.digital.controller;

import com.digital.common.BaseResponse;
import com.digital.common.ErrorCode;
import com.digital.common.ResultUtils;
import com.digital.exception.BusinessException;
import com.digital.model.dto.question.SaveAssessmentResultRequest;
import com.digital.model.entity.CareerAssessmentHistory;
import com.digital.model.entity.User;
import com.digital.model.vo.QuestionVO;
import com.digital.service.CareerAssessmentHistoryService;
import com.digital.service.QuestionService;
import com.digital.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 题目接口
 */
@RestController
@RequestMapping("/question")
@Slf4j
public class QuestionController {

    @Resource
    private QuestionService questionService;

    @Resource
    private UserService userService;

    @Resource
    private CareerAssessmentHistoryService careerAssessmentHistoryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取MBTI问卷题目列表
     *
     * @return 题目列表，包含题目ID、标题和对应的选项信息
     */
    @GetMapping("/mbti")
    public BaseResponse<List<QuestionVO>> getMbtiQuestions() {
        List<QuestionVO> questions = questionService.getQuestions(null);
        return ResultUtils.success(questions);
    }

    /**
     * 保存 MBTI（或其他测评）结果到历史记录
     */
    @PostMapping("/mbti/result/save")
    public BaseResponse<Long> saveMbtiResult(@RequestBody SaveAssessmentResultRequest req,
                                             HttpServletRequest httpServletRequest) {
        if (req == null || req.getResult() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "测评结果不能为空");
        }
        User loginUser = userService.getLoginUser(httpServletRequest);
        String type = (req.getAssessmentType() == null || req.getAssessmentType().isBlank()) ? "mbti" : req.getAssessmentType();

        try {
            CareerAssessmentHistory history = new CareerAssessmentHistory();
            history.setUserId(loginUser.getId());
            history.setAssessmentType(type);
            history.setSource(req.getSource());
            history.setAssessmentJson(objectMapper.writeValueAsString(req.getResult()));
            boolean ok = careerAssessmentHistoryService.save(history);
            if (!ok) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存测评结果失败");
            }
            return ResultUtils.success(history.getId());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("保存测评结果失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存测评结果失败");
        }
    }

    /**
     * 获取用户最新一次 MBTI（或指定类型）测评结果
     */
    @GetMapping("/mbti/result/latest")
    public BaseResponse<CareerAssessmentHistory> getLatestMbtiResult(HttpServletRequest httpServletRequest,
                                                                     @org.springframework.web.bind.annotation.RequestParam(required = false)
                                                                     String assessmentType) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        String type = (assessmentType == null || assessmentType.isBlank()) ? "mbti" : assessmentType;
        CareerAssessmentHistory latest = careerAssessmentHistoryService.lambdaQuery()
                .eq(CareerAssessmentHistory::getUserId, loginUser.getId())
                .eq(CareerAssessmentHistory::getAssessmentType, type)
                .orderByDesc(CareerAssessmentHistory::getCreateTime)
                .last("limit 1")
                .one();
        return ResultUtils.success(latest);
    }
}
