package com.digital.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.digital.model.entity.CompetitionCategory;
import com.digital.model.vo.CompetitionCategoryVO;

import java.util.List;

/**
 * 竞赛分类服务
 *
 * @author Shane
 */
public interface CompetitionCategoryService extends IService<CompetitionCategory> {

    /**
     * 获取所有分类数据（按父项目分组）
     *
     * @return 分类列表
     */
    List<CompetitionCategoryVO> getAllCategories();
}

