package com.digital.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.digital.common.BaseResponse;
import com.digital.common.ResultUtils;
import com.digital.model.dto.jobinfo.JobInfoQueryRequest;
import com.digital.model.entity.JobInfo;
import com.digital.service.BossZhiPinCrawler.BossZhiPinCrawlerService;
import com.digital.service.JobInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * 爬虫接口
 *
 * @author digital
 */
@RestController
@RequestMapping("/crawler")
@Slf4j
public class CrawlerController {

    @Resource
    private BossZhiPinCrawlerService crawlerService;

    @Resource
    private JobInfoService jobInfoService;

    /**
     * 启动BOSS直聘爬虫
     *
     * @param query    搜索关键词，如 "Java"
     * @param cityCode 城市代码，如 "101270100" (成都)
     * @param maxPages 最大爬取页面数，默认40
     * @return 响应结果
     */
    @PostMapping("/boss-zhipin/start")
    public BaseResponse<String> startCrawler(
            @RequestParam String query,
            @RequestParam String cityCode,
            @RequestParam(defaultValue = "500") Integer maxPages) {
        
        try {
            String startUrl = "https://www.zhipin.com/web/geek/job?query=" + query + "&city=" + cityCode;
            
            // 初始化爬虫参数
            log.info("爬虫初始化: query={}, cityCode={}, maxPages={}", query, cityCode, maxPages);
            crawlerService.initialize(startUrl, query, cityCode, maxPages);
            
            // 在新线程中运行爬虫，避免阻塞请求
            Thread crawlerThread = new Thread(() -> {
                try {
                    crawlerService.crawl(startUrl);
                    log.info("爬虫完成: query={}, cityCode={}", query, cityCode);
                } catch (Exception e) {
                    log.error("爬虫错误", e);
                }
            });
            crawlerThread.setName("BossZhiPin-Crawler");
            crawlerThread.setDaemon(false);
            crawlerThread.start();
            log.info("爬虫已启动，后台运行中...");
            
            return ResultUtils.success("爬虫已启动，正在后台运行中...");
        } catch (Exception e) {
            log.error("启动爬虫失败", e);
            @SuppressWarnings("unchecked")
            BaseResponse<String> errorResponse = (BaseResponse<String>) ResultUtils.error(500, "启动爬虫失败: " + e.getMessage());
            return errorResponse;
        }
    }

    /**
     * 获取爬虫状态（简单示例）
     *
     * @return 响应结果
     */
    @GetMapping("/boss-zhipin/status")
    public BaseResponse<String> getStatus() {
        return ResultUtils.success("爬虫服务运行正常");
    }

    /**
     * 快速测试接口 - 直接在浏览器访问即可启动爬虫
     * 
     * @param query    搜索关键词，默认 "Java"
     * @param cityCode 城市代码，默认 "101270100" (成都)
     * @param maxPages 最大爬取页面数，默认 40
     * @return 响应结果
     */
    @GetMapping("/boss-zhipin/test")
    public BaseResponse<String> testCrawler(
            @RequestParam(defaultValue = "Java") String query,
            @RequestParam(defaultValue = "101300600") String cityCode,
            @RequestParam(defaultValue = "40") Integer maxPages) {
        
        try {
            String startUrl = "https://www.zhipin.com/web/geek/job?query=" + query + "&city=" + cityCode;
            
            // 初始化爬虫参数
            log.info("爬虫初始化: query={}, cityCode={}, maxPages={}", query, cityCode, maxPages);
            crawlerService.initialize(startUrl, query, cityCode, maxPages);
            
            // 在新线程中运行爬虫，避免阻塞请求
            Thread crawlerThread = new Thread(() -> {
                try {
                    crawlerService.crawl(startUrl);
                    log.info("爬虫完成: query={}, cityCode={}", query, cityCode);
                } catch (Exception e) {
                    log.error("爬虫错误", e);
                }
            });
            crawlerThread.setName("BossZhiPin-Crawler");
            crawlerThread.setDaemon(false);
            crawlerThread.start();
            log.info("爬虫已启动，后台运行中...");
            
            String message = String.format("爬虫已启动！参数: 关键词=%s, 城市代码=%s, 最大页数=%d", 
                    query, cityCode, maxPages);
            return ResultUtils.success(message);
        } catch (Exception e) {
            log.error("启动爬虫失败", e);
            @SuppressWarnings("unchecked")
            BaseResponse<String> errorResponse = (BaseResponse<String>) ResultUtils.error(500, "启动爬虫失败: " + e.getMessage());
            return errorResponse;
        }
    }

    /**
     * 分页查询招聘信息
     * 支持通过 workName 字段进行模糊匹配
     *
     * @param workName   工作名称（模糊匹配，可选）
     * @param companyName 公司名称（模糊匹配，可选）
     * @param workAddress 工作地址（模糊匹配，可选）
     * @param current    当前页码，默认1
     * @param pageSize  每页数量，默认10，最大100
     * @return 分页的招聘信息列表
     */
    @GetMapping("/job-info/list/page")
    public BaseResponse<Page<JobInfo>> listJobInfoByPageGet(
            @RequestParam(required = false) String workName,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String workAddress,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        
        // 参数校验
        if (current <= 0) {
            current = 1;
        }
        if (pageSize <= 0) {
            pageSize = 10;
        }
        // 限制每页最大数量
        if (pageSize > 100) {
            pageSize = 100;
        }
        
        JobInfoQueryRequest queryRequest = new JobInfoQueryRequest();
        queryRequest.setCurrent(current);
        queryRequest.setPageSize(pageSize);
        queryRequest.setWorkName(workName);
        queryRequest.setCompanyName(companyName);
        queryRequest.setWorkAddress(workAddress);
        
        Page<JobInfo> jobInfoPage = jobInfoService.page(
                new Page<>(current, pageSize),
                jobInfoService.getQueryWrapper(queryRequest)
        );
        
        return ResultUtils.success(jobInfoPage);
    }
}

