package com.digital.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digital.common.BaseResponse;
import com.digital.common.ResultUtils;
import com.digital.model.dto.contest.ContestQueryRequest;
import com.digital.model.vo.ContestVO;
import com.digital.service.ContestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * 竞赛接口
 *
 * @author Shane
 */
@RestController
@RequestMapping("/contest")
@Slf4j
public class ContestController {

    @Resource
    private ContestService contestService;

    /**
     * 从API爬取竞赛数据并保存到数据库
     *
     * @param page    页码（默认1）
     * @param limit   每页数量（默认20）
     * @param classId 分类ID（多个用|分隔，URL编码后是%7C）
     * @param level   级别：0-不限，1-校级，2-市级，3-省级，4-全国性，5-全球性，6-自由，7-其他
     * @param sort    排序：0-报名时间，1-开赛时间，2-最近更新，3-最多浏览
     * @return 爬取到的竞赛数量
     */
    @PostMapping("/fetch")
    public BaseResponse<Integer> fetchContests(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) Integer sort) {
        try {
            log.info("开始爬取竞赛数据，page={}, limit={}, classId={}, level={}, sort={}", 
                    page, limit, classId, level, sort);
            int count = contestService.fetchAndSaveContests(page, limit, classId, level, sort);
            log.info("成功爬取并保存 {} 条竞赛数据", count);
            return ResultUtils.success(count);
        } catch (Exception e) {
            log.error("爬取竞赛数据失败", e);
            return ResultUtils.error(500, "爬取竞赛数据失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询竞赛列表
     *
     * @param contestQueryRequest 查询请求
     * @return 分页结果
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<ContestVO>> listContestVOByPage(@RequestBody ContestQueryRequest contestQueryRequest) {
        try {
            log.info("查询竞赛列表，参数: {}", contestQueryRequest);
            Page<ContestVO> contestVOPage = contestService.listContestVOByPage(contestQueryRequest);
            log.info("成功查询到 {} 条竞赛数据", contestVOPage.getTotal());
            return ResultUtils.success(contestVOPage);
        } catch (Exception e) {
            log.error("查询竞赛列表失败", e);
            return ResultUtils.error(500, "查询竞赛列表失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询竞赛列表（GET方式，方便前端调用）
     *
     * @param current     当前页（默认1）
     * @param pageSize   每页数量（默认10）
     * @param classId    分类ID（多个用逗号分隔）
     * @param level      级别：0-不限，1-校级，2-市级，3-省级，4-全国性，5-全球性，6-自由，7-其他
     * @param contestName 竞赛名称（模糊查询）
     * @param timeStatus  时间状态
     * @return 分页结果
     */
    @GetMapping("/list")
    public BaseResponse<Page<ContestVO>> listContests(
            @RequestParam(required = false, defaultValue = "1") Integer current,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) String contestName,
            @RequestParam(required = false) Integer timeStatus) {
        try {
            ContestQueryRequest contestQueryRequest = new ContestQueryRequest();
            contestQueryRequest.setCurrent(current);
            contestQueryRequest.setPageSize(pageSize);
            contestQueryRequest.setClassId(classId);
            contestQueryRequest.setLevel(level);
            contestQueryRequest.setContestName(contestName);
            contestQueryRequest.setTimeStatus(timeStatus);

            log.info("查询竞赛列表，参数: {}", contestQueryRequest);
            Page<ContestVO> contestVOPage = contestService.listContestVOByPage(contestQueryRequest);
            log.info("成功查询到 {} 条竞赛数据", contestVOPage.getTotal());
            return ResultUtils.success(contestVOPage);
        } catch (Exception e) {
            log.error("查询竞赛列表失败", e);
            return ResultUtils.error(500, "查询竞赛列表失败: " + e.getMessage());
        }
    }


    /**
     * 分页查询榜单竞赛列表（GET方式，方便前端调用）
     *
     * @param current     当前页（默认1）
     * @param pageSize   每页数量（默认10）
//     * @param classId    分类ID（多个用逗号分隔）
     * @param contestName 竞赛名称（模糊查询）
     * @param timeStatus  时间状态
     * @return 分页结果
     */
    @GetMapping("/listHonor")
    public BaseResponse<Page<ContestVO>> listHonorContests(
            @RequestParam(required = false, defaultValue = "1") Integer current,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String contestName,
            @RequestParam(required = false) Integer timeStatus) {
        try {
            ContestQueryRequest contestQueryRequest = new ContestQueryRequest();
            contestQueryRequest.setCurrent(current);
            contestQueryRequest.setPageSize(pageSize);
            contestQueryRequest.setClassId(classId);
            contestQueryRequest.setLevel(4);
            contestQueryRequest.setContestName(contestName);
            contestQueryRequest.setTimeStatus(timeStatus);

            log.info("查询竞赛列表，参数: {}", contestQueryRequest);
            Page<ContestVO> contestVOPage = contestService.listHonorContestVOByPage(contestQueryRequest);
            log.info("成功查询到 {} 条竞赛数据", contestVOPage.getTotal());
            return ResultUtils.success(contestVOPage);
        } catch (Exception e) {
            log.error("查询竞赛列表失败", e);
            return ResultUtils.error(500, "查询竞赛列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取竞赛详情
     *
     * @param contestId 竞赛ID（数据库中的contestId字段）
     * @return 竞赛详情（API返回的data部分，全量返回）
     */
    @GetMapping("/detail")
    public BaseResponse<Object> getContestDetail(@RequestParam Long contestId) {
        try {
            log.info("获取竞赛详情，contestId={}", contestId);
            Object detail = contestService.getContestDetail(contestId);
            log.info("成功获取竞赛详情，contestId={}", contestId);
            return ResultUtils.success(detail);
        } catch (Exception e) {
            log.error("获取竞赛详情失败，contestId={}", contestId, e);
            return ResultUtils.error(500, "获取竞赛详情失败: " + e.getMessage());
        }
    }
}

