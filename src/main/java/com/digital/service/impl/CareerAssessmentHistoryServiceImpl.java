package com.digital.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.digital.mapper.CareerAssessmentHistoryMapper;
import com.digital.model.entity.CareerAssessmentHistory;
import com.digital.service.CareerAssessmentHistoryService;
import org.springframework.stereotype.Service;

/**
 * 职业测评历史服务实现
 */
@Service
public class CareerAssessmentHistoryServiceImpl
        extends ServiceImpl<CareerAssessmentHistoryMapper, CareerAssessmentHistory>
        implements CareerAssessmentHistoryService {
}

