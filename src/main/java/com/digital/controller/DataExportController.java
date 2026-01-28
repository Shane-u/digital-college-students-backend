package com.digital.controller;

import com.digital.common.BaseResponse;
import com.digital.common.ResultUtils;
import com.digital.service.DataExportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.io.IOException;

/**
 * 数据导出接口（用于Python训练）
 */
@RestController
@RequestMapping("/data-export") // Adjusted RequestMapping
@Slf4j
public class DataExportController {

    @Resource
    private DataExportService dataExportService;

    /**
     * 导出竞赛推荐训练数据
     */
    @PostMapping("/contest")
    @SuppressWarnings("unchecked")
    public BaseResponse<String> exportContestData(
            @RequestParam(defaultValue = "/tmp/training_data_contest.csv") String outputPath) {
        try {
            dataExportService.exportContestTrainingData(outputPath);
            return ResultUtils.success("数据导出成功: " + outputPath);
        } catch (IOException e) {
            log.error("导出数据失败", e);
            return (BaseResponse<String>) ResultUtils.error(500, "导出失败: " + e.getMessage());
        }
    }

    /**
     * 导出职业推荐训练数据
     */
    @PostMapping("/job")
    @SuppressWarnings("unchecked")
    public BaseResponse<String> exportJobData(
            @RequestParam(defaultValue = "/tmp/training_data_job.csv") String outputPath) {
        try {
            dataExportService.exportJobTrainingData(outputPath);
            return ResultUtils.success("数据导出成功: " + outputPath);
        } catch (IOException e) {
            log.error("导出数据失败", e);
            return (BaseResponse<String>) ResultUtils.error(500, "导出失败: " + e.getMessage());
        }
    }

    /**
     * 导出用户特征数据
     */
    @PostMapping("/user-features")
    @SuppressWarnings("unchecked")
    public BaseResponse<String> exportUserFeatures(
            @RequestParam(defaultValue = "/tmp/user_features.csv") String outputPath) {
        try {
            dataExportService.exportUserFeatures(outputPath);
            return ResultUtils.success("用户特征数据导出成功: " + outputPath);
        } catch (IOException e) {
            log.error("导出用户特征数据失败", e);
            return (BaseResponse<String>) ResultUtils.error(500, "导出失败: " + e.getMessage());
        }
    }

    /**
     * 导出物品特征数据
     */
    @PostMapping("/item-features")
    @SuppressWarnings("unchecked")
    public BaseResponse<String> exportItemFeatures(
            @RequestParam String itemType,
            @RequestParam(defaultValue = "/tmp/item_features.csv") String outputPath) {
        try {
            dataExportService.exportItemFeatures(itemType, outputPath);
            return ResultUtils.success(itemType + "物品特征数据导出成功: " + outputPath);
        } catch (IOException e) {
            log.error("导出 " + itemType + " 物品特征数据失败", e);
            return (BaseResponse<String>) ResultUtils.error(500, "导出失败: " + e.getMessage());
        }
    }
}
