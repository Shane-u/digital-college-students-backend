package com.digital.config;

import com.digital.service.BossZhiPinCrawler.BossZhiPinCrawlerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 爬虫自动启动配置
 * Spring Boot启动后自动执行爬虫任务
 *
 * @author digital
 */
@Component
@Slf4j
@ConfigurationProperties(prefix = "crawler.auto-start")
public class CrawlerAutoStartConfig implements CommandLineRunner {

    @Resource
    private BossZhiPinCrawlerService crawlerService;

    /**
     * 爬虫配置（可以根据需要修改这些参数）
     */
    private boolean enabled = true;////自动爬取开关

    /**
     * 默认搜索关键词（从配置文件读取）
     */
    private String query = "Java";

    /**
     * 默认城市代码（从配置文件读取）
     */
    private String cityCode = "101270100";

    /**
     * 默认最大页数（从配置文件读取）
     */
    private int maxPages = 40;

    // Setter 方法
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public void setCityCode(String cityCode) {
        this.cityCode = cityCode;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.info("爬虫自动启动已禁用");
            return;
        }

        // 延迟启动，确保所有Bean都已初始化完成
        new Thread(() -> {
            try {
                // 等待5秒，确保应用完全启动
                Thread.sleep(5000);
                
                log.info("========================================");
                log.info("开始自动启动爬虫任务");
                log.info("参数: query={}, cityCode={}, maxPages={}", query, cityCode, maxPages);
                log.info("========================================");
                
                String startUrl = "https://www.zhipin.com/web/geek/job?query=" + query + "&city=" + cityCode;
                
                // 初始化爬虫
                crawlerService.initialize(startUrl, query, cityCode, maxPages);
                
                // 开始爬取
                crawlerService.crawl(startUrl);
                
                log.info("========================================");
                log.info("自动爬虫任务完成");
                log.info("========================================");
                
            } catch (Exception e) {
                log.error("自动启动爬虫失败", e);
            }
        }, "AutoCrawler-Thread").start();
    }
}

