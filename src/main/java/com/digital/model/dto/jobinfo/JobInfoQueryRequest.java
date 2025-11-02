package com.digital.model.dto.jobinfo;

import com.digital.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 招聘信息查询请求
 *
 * @author Shane
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class JobInfoQueryRequest extends PageRequest implements Serializable {

    /**
     * 工作名称（模糊匹配）
     */
    private String workName;

    /**
     * 公司名称（模糊匹配）
     */
    private String companyName;

    /**
     * 工作地址（模糊匹配）
     */
    private String workAddress;

    /**
     * 薪水范围（模糊匹配）
     */
    private String workSalary;

    /**
     * 工作年限
     */
    private String workYear;

    /**
     * 学历要求
     */
    private String graduate;

    private static final long serialVersionUID = 1L;
}

