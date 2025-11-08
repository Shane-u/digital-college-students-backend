package com.digital.controller;

import com.digital.common.BaseResponse;
import com.digital.common.ResultUtils;
import com.digital.model.vo.CompetitionCategoryVO;
import com.digital.service.CompetitionCategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 竞赛分类接口
 *
 * @author Shane
 */
@RestController
@RequestMapping("/competition/category")
@Slf4j
public class CompetitionCategoryController {

    @Resource
    private CompetitionCategoryService competitionCategoryService;

    /**
     * 获取所有竞赛分类
     *
     * @return 分类列表
     */
    @GetMapping("/list")
    public BaseResponse<List<CompetitionCategoryVO>> getAllCategories() {
        try {
            log.info("开始获取竞赛分类数据");
            List<CompetitionCategoryVO> categories = competitionCategoryService.getAllCategories();
            log.info("成功获取 {} 个分类组", categories.size());
            return ResultUtils.success(categories);
        } catch (Exception e) {
            log.error("获取竞赛分类数据失败", e);
            return ResultUtils.error(500, "获取竞赛分类数据失败: " + e.getMessage());
        }
    }
}

