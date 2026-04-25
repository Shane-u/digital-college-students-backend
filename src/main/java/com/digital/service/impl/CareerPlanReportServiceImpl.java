package com.digital.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.digital.mapper.CareerPlanReportMapper;
import com.digital.model.entity.CareerPlanReport;
import com.digital.service.CareerPlanReportService;
import org.springframework.stereotype.Service;

/**
 * 职业规划报告历史服务实现
 */
@Service
public class CareerPlanReportServiceImpl extends ServiceImpl<CareerPlanReportMapper, CareerPlanReport>
        implements CareerPlanReportService {
}

