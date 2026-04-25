package com.digital.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digital.common.BaseResponse;
import com.digital.common.DeleteRequest;
import com.digital.common.ErrorCode;
import com.digital.common.ResultUtils;
import com.digital.exception.BusinessException;
import com.digital.model.entity.CareerPlanReport;
import com.digital.model.entity.User;
import com.digital.service.CareerPlanReportService;
import com.digital.service.UserService;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 职业规划报告历史接口
 */
@RestController
@RequestMapping("/career-plan/report")
@Slf4j
public class CareerPlanReportController {

    @Resource
    private CareerPlanReportService careerPlanReportService;

    @Resource
    private UserService userService;

    /**
     * 保存/更新一份职业规划报告（前端传最终内容）
     */
    @PostMapping("/save")
    public BaseResponse<Long> save(@RequestBody SaveCareerPlanReportRequest req, HttpServletRequest httpServletRequest) {
        if (req == null || StringUtils.isBlank(req.getRunId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "runId 不能为空");
        }
        if (StringUtils.isBlank(req.getReportMarkdown()) && StringUtils.isBlank(req.getError())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "reportMarkdown / error 至少填一个");
        }
        User loginUser = userService.getLoginUser(httpServletRequest);

        CareerPlanReport toSave = new CareerPlanReport();
        toSave.setUserId(loginUser.getId());
        toSave.setRunId(req.getRunId());
        toSave.setReportMarkdown(req.getReportMarkdown());
        toSave.setError(req.getError());

        // 若同一个 runId 已存在则更新
        CareerPlanReport exist = careerPlanReportService.lambdaQuery()
                .eq(CareerPlanReport::getUserId, loginUser.getId())
                .eq(CareerPlanReport::getRunId, req.getRunId())
                .last("limit 1")
                .one();
        if (exist != null) {
            toSave.setId(exist.getId());
            boolean ok = careerPlanReportService.updateById(toSave);
            if (!ok) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新报告失败");
            }
            return ResultUtils.success(exist.getId());
        }

        boolean ok = careerPlanReportService.save(toSave);
        if (!ok) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存报告失败");
        }
        return ResultUtils.success(toSave.getId());
    }

    /**
     * 删除报告（逻辑删除，仅能删除自己的）
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> delete(@RequestBody DeleteRequest deleteRequest, HttpServletRequest httpServletRequest) {
        if (deleteRequest == null || StringUtils.isBlank(deleteRequest.getId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(httpServletRequest);
        CareerPlanReport report = careerPlanReportService.getById(deleteRequest.getId());
        if (report == null || !loginUser.getId().equals(report.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权限删除该报告");
        }
        return ResultUtils.success(careerPlanReportService.removeById(deleteRequest.getId()));
    }

    /**
     * 分页查询当前用户的报告历史
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<CareerPlanReport>> listByPage(@RequestBody CareerPlanReportQueryRequest req,
                                                           HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        int current = req != null ? req.getCurrent() : 1;
        int pageSize = req != null ? req.getPageSize() : 10;
        Page<CareerPlanReport> page = careerPlanReportService.lambdaQuery()
                .eq(CareerPlanReport::getUserId, loginUser.getId())
                .orderByDesc(CareerPlanReport::getCreateTime)
                .page(new Page<>(current, pageSize));
        return ResultUtils.success(page);
    }

    /**
     * 获取报告详情（仅能查看自己的）
     */
    @GetMapping("/get")
    public BaseResponse<CareerPlanReport> get(@RequestParam String id, HttpServletRequest httpServletRequest) {
        if (StringUtils.isBlank(id)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(httpServletRequest);
        CareerPlanReport report = careerPlanReportService.getById(id);
        if (report == null || !loginUser.getId().equals(report.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权限查看该报告");
        }
        return ResultUtils.success(report);
    }

    @Data
    public static class SaveCareerPlanReportRequest {
        private String runId;
        private String reportMarkdown;
        private String error;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class CareerPlanReportQueryRequest extends com.digital.common.PageRequest {
    }
}

