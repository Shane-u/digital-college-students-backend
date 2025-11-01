package com.digital.controller;

import com.digital.common.BaseResponse;
import com.digital.common.ResultUtils;
import com.digital.service.BossZhiPinCrawler.BossZhiPinCrawlerService;
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
}

