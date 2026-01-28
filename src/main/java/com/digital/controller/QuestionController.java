package com.digital.controller;

import com.digital.common.BaseResponse;
import com.digital.common.ResultUtils;
import com.digital.model.vo.QuestionVO;
import com.digital.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
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

    /**
     * 获取MBTI问卷题目列表
     *
     * @return 题目列表，包含题目ID、标题和对应的选项信息
     */
    @GetMapping("/mbti")
    public BaseResponse<List<QuestionVO>> getMbtiQuestions() {
        try {
            List<QuestionVO> questions = questionService.getQuestions(null);
            return ResultUtils.success(questions);
        } catch (Exception e) {
            log.error("获取MBTI问卷题目失败", e);
            return ResultUtils.error(500, "获取题目失败: " + e.getMessage());
        }
    }
}
