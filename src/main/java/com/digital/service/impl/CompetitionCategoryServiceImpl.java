package com.digital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.digital.mapper.CompetitionCategoryMapper;
import com.digital.model.entity.CompetitionCategory;
import com.digital.model.vo.CompetitionCategoryVO;
import com.digital.service.CompetitionCategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 竞赛分类服务实现
 *
 * @author Shane
 */
@Service
@Slf4j
public class CompetitionCategoryServiceImpl extends ServiceImpl<CompetitionCategoryMapper, CompetitionCategory>
        implements CompetitionCategoryService {

    @Override
    public List<CompetitionCategoryVO> getAllCategories() {
        try {
            // 查询所有未删除的分类
            QueryWrapper<CompetitionCategory> queryWrapper = new QueryWrapper<>();
            queryWrapper.orderByAsc("parentName", "categoryId");
            List<CompetitionCategory> categories = this.list(queryWrapper);

            if (categories == null || categories.isEmpty()) {
                log.warn("未找到竞赛分类数据");
                return new ArrayList<>();
            }

            // 按父项目名字分组
            Map<String, List<CompetitionCategory>> groupedByParent = categories.stream()
                    .collect(Collectors.groupingBy(CompetitionCategory::getParentName));

            // 转换为VO对象
            List<CompetitionCategoryVO> result = new ArrayList<>();
            for (Map.Entry<String, List<CompetitionCategory>> entry : groupedByParent.entrySet()) {
                CompetitionCategoryVO vo = new CompetitionCategoryVO();
                vo.setName(entry.getKey());

                // 转换子分类列表
                List<CompetitionCategoryVO.CategoryItem> sons = entry.getValue().stream()
                        .map(category -> {
                            CompetitionCategoryVO.CategoryItem item = new CompetitionCategoryVO.CategoryItem();
                            item.setLabel(category.getCategoryName());
                            item.setValue(category.getCategoryId());
                            return item;
                        })
                        .collect(Collectors.toList());

                vo.setSons(sons);
                result.add(vo);
            }

            log.info("成功获取 {} 个父分类，共 {} 个子分类", result.size(), categories.size());
            return result;
        } catch (Exception e) {
            log.error("获取竞赛分类数据失败", e);
            throw new RuntimeException("获取竞赛分类数据失败: " + e.getMessage());
        }
    }
}

