package com.digital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.digital.constant.CommonConstant;
import com.digital.mapper.JobInfoMapper;
import com.digital.model.dto.jobinfo.JobInfoQueryRequest;
import com.digital.model.entity.JobInfo;
import com.digital.service.JobInfoService;
import com.digital.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 招聘信息服务实现
 *
 * @author Shane
 */
@Service
@Slf4j
public class JobInfoServiceImpl extends ServiceImpl<JobInfoMapper, JobInfo> implements JobInfoService {

    @Override
    public QueryWrapper<JobInfo> getQueryWrapper(JobInfoQueryRequest jobInfoQueryRequest) {
        QueryWrapper<JobInfo> queryWrapper = new QueryWrapper<>();
        if (jobInfoQueryRequest == null) {
            return queryWrapper;
        }
        
        String workName = jobInfoQueryRequest.getWorkName();
        String companyName = jobInfoQueryRequest.getCompanyName();
        String workAddress = jobInfoQueryRequest.getWorkAddress();
        String workSalary = jobInfoQueryRequest.getWorkSalary();
        String workYear = jobInfoQueryRequest.getWorkYear();
        String graduate = jobInfoQueryRequest.getGraduate();
        
        // 拼接查询条件 - like
        queryWrapper.like(StringUtils.isNotBlank(workName), "workName", workName);
        queryWrapper.like(StringUtils.isNotBlank(companyName), "companyName", companyName);
        queryWrapper.like(StringUtils.isNotBlank(workAddress), "workAddress", workAddress);
        queryWrapper.like(StringUtils.isNotBlank(workSalary), "workSalary", workSalary);
        queryWrapper.eq(StringUtils.isNotBlank(workYear), "workYear", workYear);
        queryWrapper.eq(StringUtils.isNotBlank(graduate), "graduate", graduate);
        
        // 默认按创建时间倒序
        queryWrapper.orderByDesc("createTime");
        
        return queryWrapper;
    }
}

