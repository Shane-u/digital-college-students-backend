package com.digital.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 竞赛分类视图对象
 *
 * @author Shane
 */
@Data
public class CompetitionCategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 父项目名字
     */
    private String name;

    /**
     * 子分类列表
     */
    private List<CategoryItem> sons;

    /**
     * 分类项
     */
    @Data
    public static class CategoryItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 标签（当前项目名字）
         */
        private String label;

        /**
         * 值（当前项目的id）
         */
        private Integer value;
    }
}

