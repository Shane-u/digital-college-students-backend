package com.digital.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.digital.model.dto.contest.ContestQueryRequest;
import com.digital.model.entity.Contest;
import com.digital.model.vo.ContestVO;

/**
 * 竞赛服务
 *
 * @author Shane
 */
public interface ContestService extends IService<Contest> {

    /**
     * 从API爬取竞赛数据并保存到数据库
     *
     * @param page     页码
     * @param limit    每页数量
     * @param classId  分类ID（多个用|分隔，URL编码后是%7C）
     * @param level    级别：0-不限，1-校级，2-市级，3-省级，4-全国性，5-全球性，6-自由，7-其他
     * @param sort     排序：0-报名时间，1-开赛时间，2-最近更新，3-最多浏览
     * @return 爬取到的竞赛数量
     */
    int fetchAndSaveContests(Integer page, Integer limit, String classId, Integer level, Integer sort);

    /**
     * 获取查询条件
     *
     * @param contestQueryRequest 查询请求
     * @return 查询条件
     */
    QueryWrapper<Contest> getQueryWrapper(ContestQueryRequest contestQueryRequest);

    /**
     * 获取榜单竞赛查询条件
     *
     * @param contestQueryRequest 查询请求
     * @return 查询条件
     */
    QueryWrapper<Contest> getHonorQueryWrapper(ContestQueryRequest contestQueryRequest);

    /**
     * 获取竞赛封装
     *
     * @param contest 竞赛实体
     * @return 竞赛VO
     */
    ContestVO getContestVO(Contest contest);

    /**
     * 分页查询竞赛列表
     *
     * @param contestQueryRequest 查询请求
     * @return 分页结果
     */
    Page<ContestVO> listContestVOByPage(ContestQueryRequest contestQueryRequest);


    /**
     * 分页查询榜单竞赛列表
     *
     * @param contestQueryRequest 查询请求
     * @return 分页结果
     */
    Page<ContestVO> listHonorContestVOByPage(ContestQueryRequest contestQueryRequest);

    /**
     * 获取竞赛详情
     *
     * @param contestId 竞赛ID（数据库中的contestId字段）
     * @return 竞赛详情（API返回的data部分）
     */
    Object getContestDetail(Long contestId);
}

