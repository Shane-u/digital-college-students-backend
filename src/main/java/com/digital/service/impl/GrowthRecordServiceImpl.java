package com.digital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.digital.common.ErrorCode;
import com.digital.exception.BusinessException;
import com.digital.exception.ThrowUtils;
import com.digital.mapper.GrowthFileMapper;
import com.digital.mapper.GrowthImageMapper;
import com.digital.mapper.GrowthRecordMapper;
import com.digital.model.dto.growthrecord.GrowthRecordQueryRequest;
import com.digital.model.entity.GrowthFile;
import com.digital.model.entity.GrowthImage;
import com.digital.model.entity.GrowthRecord;
import com.digital.model.vo.GrowthFileVO;
import com.digital.model.vo.GrowthImageVO;
import com.digital.model.vo.GrowthRecordEventVO;
import com.digital.model.vo.GrowthRecordStatisticsVO;
import com.digital.model.vo.GrowthRecordVO;
import com.digital.model.vo.MilestoneStatisticsVO;
import com.digital.service.GrowthRecordService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 成长记录服务实现
 *
 * @author Shane
 */
@Service
@Slf4j
public class GrowthRecordServiceImpl extends ServiceImpl<GrowthRecordMapper, GrowthRecord>
        implements GrowthRecordService {

    @Resource
    private GrowthImageMapper growthImageMapper;

    @Resource
    private GrowthFileMapper growthFileMapper;

    @Override
    public void validGrowthRecord(GrowthRecord growthRecord, boolean add) {
        if (growthRecord == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String eventDesc = growthRecord.getEventDesc();
        BigDecimal importance = growthRecord.getImportance();
        // 创建时，事件描述不能为空
        if (add) {
            ThrowUtils.throwIf(StringUtils.isBlank(eventDesc), ErrorCode.PARAMS_ERROR, "事件描述不能为空");
        }
        // 有参数则校验
        if (StringUtils.isNotBlank(eventDesc) && eventDesc.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "事件描述过长");
        }
        // 重要程度校验（0-5，支持0.5的倍数）
        if (importance != null) {
            if (importance.compareTo(BigDecimal.ZERO) < 0 || importance.compareTo(new BigDecimal("5")) > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "重要程度必须在0-5之间");
            }
        }
    }

    @Override
    public QueryWrapper<GrowthRecord> getQueryWrapper(GrowthRecordQueryRequest growthRecordQueryRequest) {
        QueryWrapper<GrowthRecord> queryWrapper = new QueryWrapper<>();
        if (growthRecordQueryRequest == null) {
            return queryWrapper;
        }
        Long userId = growthRecordQueryRequest.getUserId();
        String eventDesc = growthRecordQueryRequest.getEventDesc();
        BigDecimal minImportance = growthRecordQueryRequest.getMinImportance();
        // 拼接查询条件
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StringUtils.isNotBlank(eventDesc), "eventDesc", eventDesc);
        queryWrapper.ge(ObjectUtils.isNotEmpty(minImportance), "importance", minImportance);
        queryWrapper.orderByDesc("recordTime", "createTime");
        return queryWrapper;
    }

    @Override
    public GrowthRecordVO getGrowthRecordVO(GrowthRecord growthRecord) {
        if (growthRecord == null) {
            return null;
        }
        GrowthRecordVO growthRecordVO = new GrowthRecordVO();
        BeanUtils.copyProperties(growthRecord, growthRecordVO);

        // 查询关联的图片
        QueryWrapper<GrowthImage> imageQueryWrapper = new QueryWrapper<>();
        imageQueryWrapper.eq("growthRecordId", growthRecord.getId());
        List<GrowthImage> images = growthImageMapper.selectList(imageQueryWrapper);
        List<GrowthImageVO> imageVOList = images.stream().map(image -> {
            GrowthImageVO imageVO = new GrowthImageVO();
            BeanUtils.copyProperties(image, imageVO);
            return imageVO;
        }).collect(Collectors.toList());
        growthRecordVO.setImages(imageVOList);

        // 查询关联的文件
        QueryWrapper<GrowthFile> fileQueryWrapper = new QueryWrapper<>();
        fileQueryWrapper.eq("growthRecordId", growthRecord.getId());
        List<GrowthFile> files = growthFileMapper.selectList(fileQueryWrapper);
        List<GrowthFileVO> fileVOList = files.stream().map(file -> {
            GrowthFileVO fileVO = new GrowthFileVO();
            BeanUtils.copyProperties(file, fileVO);
            return fileVO;
        }).collect(Collectors.toList());
        growthRecordVO.setFiles(fileVOList);

        return growthRecordVO;
    }

    @Override
    public GrowthRecordStatisticsVO getStatistics(Long userId) {
        GrowthRecordStatisticsVO statistics = new GrowthRecordStatisticsVO();

        // 统计记录总数
        QueryWrapper<GrowthRecord> recordQueryWrapper = new QueryWrapper<>();
        recordQueryWrapper.eq("userId", userId);
        Long recordCount = this.count(recordQueryWrapper);
        statistics.setRecordCount(recordCount);

        // 统计照片总数
        QueryWrapper<GrowthImage> imageQueryWrapper = new QueryWrapper<>();
        imageQueryWrapper.eq("userId", userId);
        imageQueryWrapper.eq("type",2);
        Long imageCount = growthImageMapper.selectCount(imageQueryWrapper);
        statistics.setImageCount(imageCount);

        // 统计文件总数
        QueryWrapper<GrowthFile> fileQueryWrapper = new QueryWrapper<>();
        fileQueryWrapper.eq("userId", userId);
        Long fileCount = growthFileMapper.selectCount(fileQueryWrapper);
        statistics.setFileCount(fileCount);

        return statistics;
    }

    @Override
    public List<GrowthRecordEventVO> getEventList(Long userId) {
        QueryWrapper<GrowthRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        queryWrapper.select("id", "eventDesc");
        queryWrapper.orderByDesc("recordTime", "createTime");
        List<GrowthRecord> records = this.list(queryWrapper);
        return records.stream().map(record -> {
            GrowthRecordEventVO eventVO = new GrowthRecordEventVO();
            eventVO.setId(record.getId());
            eventVO.setEventDesc(record.getEventDesc());
            return eventVO;
        }).collect(Collectors.toList());
    }

    @Override
    public List<GrowthRecordVO> searchByEventDesc(String eventDesc, Long userId) {
        QueryWrapper<GrowthRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        queryWrapper.like(StringUtils.isNotBlank(eventDesc), "eventDesc", eventDesc);
        queryWrapper.orderByDesc("recordTime", "createTime");
        List<GrowthRecord> records = this.list(queryWrapper);
        return records.stream().map(this::getGrowthRecordVO).collect(Collectors.toList());
    }

    @Override
    public MilestoneStatisticsVO getMilestoneStatistics(Long userId) {
        MilestoneStatisticsVO statistics = new MilestoneStatisticsVO();

        // 统计里程碑总数（4星及以上）
        QueryWrapper<GrowthRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        queryWrapper.ge("importance", new BigDecimal("4"));
        Long milestoneCount = this.count(queryWrapper);
        statistics.setMilestoneCount(milestoneCount);

        // 获取最新的成长记录时间
        queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        queryWrapper.ge("importance", new BigDecimal("4"));
        queryWrapper.orderByDesc("recordTime", "createTime");
        queryWrapper.last("limit 1");
        GrowthRecord latestRecord = this.getOne(queryWrapper);
        if (latestRecord != null) {
            statistics.setLatestRecordTime(latestRecord.getRecordTime() != null
                    ? latestRecord.getRecordTime()
                    : latestRecord.getCreateTime());
        }

        // 获取最早的成长记录时间，计算总时长（当前时间 - 最早记录时间）
        queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        queryWrapper.ge("importance", new BigDecimal("4"));
        queryWrapper.orderByAsc("recordTime", "createTime");
        queryWrapper.last("limit 1");
        GrowthRecord earliestRecord = this.getOne(queryWrapper);
        if (earliestRecord != null) {
            Date earliestTime = earliestRecord.getRecordTime() != null
                    ? earliestRecord.getRecordTime()
                    : earliestRecord.getCreateTime();
            if (earliestTime != null) {
                // 计算总时长（当前时间 - 最早记录时间）
                Date currentTime = new Date();
                long diff = currentTime.getTime() - earliestTime.getTime();
                long days = diff / (1000 * 60 * 60 * 24) + 1; // 确保有1天
                statistics.setTotalDays(days);
            } else {
                statistics.setTotalDays(0L);
            }
        } else {
            statistics.setTotalDays(0L);
        }

        return statistics;
    }
}

