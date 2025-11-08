package com.digital.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.digital.model.dto.growthrecord.GrowthRecordQueryRequest;
import com.digital.model.entity.GrowthRecord;
import com.digital.model.vo.GrowthRecordEventVO;
import com.digital.model.vo.GrowthRecordStatisticsVO;
import com.digital.model.vo.GrowthRecordVO;
import com.digital.model.vo.MilestoneStatisticsVO;
import java.util.List;

/**
 * 成长记录服务
 *
 * @author Shane
 */
public interface GrowthRecordService extends IService<GrowthRecord> {

    /**
     * 校验成长记录
     *
     * @param growthRecord 成长记录
     * @param add          是否为新增
     */
    void validGrowthRecord(GrowthRecord growthRecord, boolean add);

    /**
     * 获取查询条件
     *
     * @param growthRecordQueryRequest 查询请求
     * @return 查询条件
     */
    QueryWrapper<GrowthRecord> getQueryWrapper(GrowthRecordQueryRequest growthRecordQueryRequest);

    /**
     * 获取成长记录封装
     *
     * @param growthRecord 成长记录
     * @return 成长记录VO
     */
    GrowthRecordVO getGrowthRecordVO(GrowthRecord growthRecord);

    /**
     * 获取统计信息
     *
     * @param userId 用户ID
     * @return 统计信息
     */
    GrowthRecordStatisticsVO getStatistics(Long userId);

    /**
     * 获取事件列表
     *
     * @param userId 用户ID
     * @return 事件列表
     */
    List<GrowthRecordEventVO> getEventList(Long userId);

    /**
     * 模糊查询成长记录
     *
     * @param eventDesc 事件描述
     * @param userId    用户ID
     * @return 成长记录列表
     */
    List<GrowthRecordVO> searchByEventDesc(String eventDesc, Long userId);

    /**
     * 获取里程碑统计信息
     *
     * @param userId 用户ID
     * @return 里程碑统计信息
     */
    MilestoneStatisticsVO getMilestoneStatistics(Long userId);
}

