package com.digital.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.digital.model.dto.jobinfo.JobInfoQueryRequest;
import com.digital.model.entity.JobInfo;

/**
 * 招聘信息服务
 *
 * @author Shane
 */
public interface JobInfoService extends IService<JobInfo> {

    /**
     * 获取查询条件
     *
     * @param jobInfoQueryRequest 查询请求
     * @return 查询包装器
     */
    QueryWrapper<JobInfo> getQueryWrapper(JobInfoQueryRequest jobInfoQueryRequest);
}

