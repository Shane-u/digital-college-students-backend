package com.digital.service.BossZhiPinCrawler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.digital.mapper.JobInfoMapper;
import com.digital.model.entity.JobInfo;
import jakarta.annotation.Resource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BOSS直聘爬虫主类
 *
 * @author digital
 */
@Service
@Slf4j
public class BossZhiPinCrawlerService {
    private Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    // HttpClient实例
    private HttpClient httpClient;
    // 要爬取的域名限制
    private String domain;
    // Chrome下载器
    private ChromeDownloaderService chromeDownloader;
    // 页面计数器
    private AtomicInteger pageCounter = new AtomicInteger(1);
    // 最大爬取页面数
    private int maxPages;
    // 工作信息列表
    private List<WorkInfService> workInfList = new ArrayList<>();
    // 搜索关键词
    private String query;
    // 城市代码
    private String cityCode;

    @Resource
    private JobInfoMapper jobInfoMapper;

    public BossZhiPinCrawlerService() {
    }

    public BossZhiPinCrawlerService(String startUrl, String query, String cityCode, int maxPages) {
        initialize(startUrl, query, cityCode, maxPages);
    }

    /**
     * 初始化爬虫参数
     *
     * @param startUrl 起始URL
     * @param query 搜索关键词
     * @param cityCode 城市代码
     * @param maxPages 最大爬取页面数
     */
    public void initialize(String startUrl, String query, String cityCode, int maxPages) {
        log.info("初始化爬虫: query={}, cityCode={}, maxPages={}", query, cityCode, maxPages);
        
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        try {
            URI uri = new URI(startUrl);
            this.domain = uri.getHost();
        } catch (URISyntaxException e) {
            log.error("URL解析失败: {}", startUrl, e);
        }

        try {
            this.chromeDownloader = new ChromeDownloaderService();
            log.info("Chrome下载器初始化成功");
        } catch (Exception e) {
            log.error("Chrome下载器初始化失败", e);
            throw new RuntimeException("Chrome下载器初始化失败", e);
        }
        
        this.maxPages = maxPages;
        this.query = query;
        this.cityCode = cityCode;
        this.visitedUrls.clear();
        this.workInfList.clear();
        this.pageCounter.set(1);
    }

    /**
     * 开始爬取
     * @param startUrl 起始URL
     */
    public void crawl(String startUrl) {
        log.info("开始爬取: {}", startUrl);
        
        if (chromeDownloader == null) {
            log.error("Chrome下载器未初始化，无法开始爬取");
            throw new IllegalStateException("Chrome下载器未初始化，请先调用initialize方法");
        }
        
        Set<String> toVisit = new HashSet<>();
        toVisit.add(startUrl);

        int pagesCrawled = 0;
        int detailPagesCrawled = 0;
        int validJobsSaved = 0;
        
        while (!toVisit.isEmpty() && pagesCrawled < maxPages) {
            String url = toVisit.iterator().next();
            toVisit.remove(url);

            if (visitedUrls.contains(url)) {
                continue;
            }

            try {
                System.out.println("[爬取进度] 第" + (pagesCrawled + 1) + "页: " + url);
                String htmlContent = fetchHtmlContent(url);
                
                if (htmlContent == null || htmlContent.isEmpty()) {
                    log.warn("HTML内容为空，跳过: {}", url);
                    continue;
                }
                
                visitedUrls.add(url);
                pagesCrawled++;

                // 处理页面内容
                if (url.contains("/job_detail/")) {
                    // 详情页
                    detailPagesCrawled++;
                    boolean saved = processDetailPage(htmlContent, url);
                    if (saved) {
                        validJobsSaved++;
                    }
                } else {
                    // 列表页
                    Set<String> jobUrls = processListPage(htmlContent, url);
                    // 将提取到的职位链接添加到待访问队列
                    for (String jobUrl : jobUrls) {
                        if (!visitedUrls.contains(jobUrl)) {
                            toVisit.add(jobUrl);
                        }
                    }

                    // 添加下一页
                    int nextPage = pageCounter.incrementAndGet();
                    String nextPageUrl = "https://www.zhipin.com/web/geek/job?query=" + query +
                            "&city=" + cityCode + "&page=" + nextPage;
                    if (!visitedUrls.contains(nextPageUrl) && nextPage <= maxPages) {
                        toVisit.add(nextPageUrl);
                    }
                }

                // 从页面中提取其他链接作为备用（优先级较低）
                Set<String> links = extractLinks(htmlContent, url);
                for (String link : links) {
                    if (!visitedUrls.contains(link) && isSameDomain(link) && link.contains("/job_detail/")) {
                        toVisit.add(link);
                    }
                }

                // 礼貌性延迟，避免请求过于频繁（因为页面加载时间已增加，这里可以减少延迟）
                Thread.sleep(2000);

            } catch (Exception e) {
                log.error("爬取出错: {}", url, e);
            }
        }

        System.out.println("[爬取完成] 共爬取 " + pagesCrawled + " 页（其中详情页 " + detailPagesCrawled + " 页），获取 " + validJobsSaved + " 条有效职位信息");
        log.info("爬取完成: 总页数={}, 详情页={}, 有效职位={}", pagesCrawled, detailPagesCrawled, validJobsSaved);
        
        // 关闭浏览器
        try {
            if (chromeDownloader != null) {
                chromeDownloader.close();
            }
        } catch (Exception e) {
            log.error("关闭浏览器出错", e);
        }

        // 保存结果到文件
        saveResultsToFile();
    }

    /**
     * 获取HTML内容
     */
    private String fetchHtmlContent(String url) {
        // 使用ChromeDriver获取页面内容
        return chromeDownloader.download(url);
    }

    /**
     * 从HTML中提取链接
     */
    private Set<String> extractLinks(String html, String baseUrl) {
        Set<String> links = new HashSet<>();
        try {
            Document doc = Jsoup.parse(html, baseUrl);
            Elements linkElements = doc.select("a[href]");

            for (Element link : linkElements) {
                String href = link.attr("abs:href"); // 获取绝对URL
                if (!href.isEmpty() && !href.startsWith("javascript:") && href.contains("zhipin.com")) {
                    links.add(href);
                }
            }
        } catch (Exception e) {
            System.err.println("解析链接时出错: " + e.getMessage());
        }
        return links;
    }

    /**
     * 处理列表页面，返回提取到的职位链接集合
     */
    private Set<String> processListPage(String html, String url) {
        Set<String> jobUrls = new HashSet<>();
        try {
            Document doc = Jsoup.parse(html);

            // 提取职位链接 - 使用多种选择器确保匹配
            Elements jobLinks = doc.select(
                "a.job-card-left, " +
                "a[ka*='job_list'], " +
                ".job-list-box a[href*='/job_detail/'], " +
                ".job-card-wrapper a[href*='/job_detail/'], " +
                ".job-list a[href*='/job_detail/'], " +
                "a[href*='/job_detail/'], " +
                ".job-primary-wrapper a[href*='/job_detail/'], " +
                ".job-card a[href*='/job_detail/'], " +
                "[class*=job-card] a[href*='/job_detail/'], " +
                "[class*=job-item] a[href*='/job_detail/']"
            );
            
            // 如果上面的选择器没有结果，尝试更宽泛的选择
            if (jobLinks.isEmpty()) {
                // 查找所有包含job_detail的链接
                jobLinks = doc.select("a[href*='job_detail']");
                log.debug("使用宽泛选择器，找到 {} 个包含job_detail的链接", jobLinks.size());
            }
            
            int linkCount = 0;
            int invalidCount = 0;
            for (Element link : jobLinks) {
                String jobUrl = link.attr("abs:href");
                // 处理相对路径
                if (jobUrl.isEmpty()) {
                    jobUrl = link.attr("href");
                    if (!jobUrl.isEmpty() && !jobUrl.startsWith("http")) {
                        try {
                            URI baseUri = new URI(url);
                            URI jobUri = baseUri.resolve(jobUrl);
                            jobUrl = jobUri.toString();
                        } catch (URISyntaxException e) {
                            log.debug("URL解析失败: {}", jobUrl);
                            invalidCount++;
                            continue;
                        }
                    }
                }
                
                // 验证URL是否有效（必须是完整的job_detail URL）
                // URL格式：https://www.zhipin.com/job_detail/xxxxx.html 或 https://www.zhipin.com/job_detail/xxxxx.html?xxx
                if (!jobUrl.isEmpty() && 
                    jobUrl.contains("/job_detail/") && 
                    jobUrl.contains("zhipin.com") &&
                    !jobUrl.equals("https://www.zhipin.com/job_detail/") && // 排除不完整的URL
                    !jobUrl.matches(".*/job_detail/\\s*$") && // 排除只有路径没有ID的URL
                    !jobUrl.matches(".*/job_detail/\\?.*") && // 排除只有参数没有ID的URL
                    jobUrl.length() > 40 && // 确保URL足够长（完整的URL应该超过40字符）
                    !visitedUrls.contains(jobUrl)) {
                    // 检查URL是否包含有效的job ID（通常是字母数字组合）
                    String[] parts = jobUrl.split("/job_detail/");
                    if (parts.length > 1) {
                        String idPart = parts[1].split("\\?")[0]; // 去掉参数部分
                        if (idPart.length() > 5 && (idPart.contains(".html") || idPart.matches("[a-zA-Z0-9]+"))) {
                            jobUrls.add(jobUrl);
                            linkCount++;
                        } else {
                            invalidCount++;
                        }
                    } else {
                        invalidCount++;
                    }
                } else {
                    invalidCount++;
                }
            }
            
            if (linkCount > 0) {
                System.out.println("[列表页] 提取到 " + linkCount + " 个有效职位链接，URL: " + url);
                log.info("列表页提取到 {} 个有效职位链接", linkCount);
            } else {
                // 输出调试信息
                log.warn("列表页未提取到任何职位链接，URL: {}, 找到的链接总数: {}, 无效链接: {}", 
                    url, jobLinks.size(), invalidCount);
                // 输出HTML片段帮助调试
                if (html.length() > 500) {
                    log.debug("HTML片段（前500字符）: {}", html.substring(0, Math.min(500, html.length())));
                }
            }

        } catch (Exception e) {
            log.error("处理列表页时出错: {}", url, e);
        }
        
        return jobUrls;
    }

    /**
     * 处理详情页面
     * @return 是否成功保存到数据库
     */
    private boolean processDetailPage(String html, String url) {
        try {
            Document doc = Jsoup.parse(html);

            // 检查是否已存在该URL的记录，避免重复存储
            QueryWrapper<JobInfo> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("url", url);
            queryWrapper.eq("isDelete", 0);
            Long count = jobInfoMapper.selectCount(queryWrapper);
            if (count > 0) {
                log.debug("职位信息已存在，跳过: {}", url);
                return false;
            }

            JobInfo jobInfo = new JobInfo();
            jobInfo.setUrl(url);

            // 提取工作名称 - 使用多种选择器确保匹配
            Element nameElement = doc.selectFirst(
                "div.name h1, " +
                ".job-name h1, " +
                "h1.job-name, " +
                ".job-detail-header h1, " +
                ".job-header h1, " +
                "h1.name, " +
                ".name-box h1, " +
                "h1, " +
                ".job-title, " +
                "[class*=job-name] h1, " +
                "[class*=job-title], " +
                ".job-primary .name h1"
            );
            if (nameElement != null) {
                String workName = nameElement.text().trim();
                if (!workName.isEmpty()) {
                    jobInfo.setWorkName(workName);
                }
            }
            
            // 如果第一次提取失败，尝试从其他位置提取
            if (jobInfo.getWorkName() == null || jobInfo.getWorkName().isEmpty()) {
                // 尝试从job-primary区域提取
                Element jobPrimary = doc.selectFirst(".job-primary, .job-primary-box");
                if (jobPrimary != null) {
                    Element nameEl = jobPrimary.selectFirst("h1, .name, [class*=name]");
                    if (nameEl != null) {
                        String text = nameEl.text().trim();
                        if (!text.isEmpty() && text.length() < 100) {
                            jobInfo.setWorkName(text);
                            log.debug("从job-primary区域提取到职位名称: {}", text);
                        }
                    }
                }
            }
            
            // 如果还是失败，尝试更宽泛的搜索
            if (jobInfo.getWorkName() == null || jobInfo.getWorkName().isEmpty()) {
                Elements nameElements = doc.select("h1, h2.title, [class*=title]:not([class*=salary])");
                for (Element el : nameElements) {
                    String text = el.text().trim();
                    // 过滤掉明显不是职位名称的文本
                    if (text != null && !text.isEmpty() && 
                        text.length() > 2 && text.length() < 100 && 
                        !text.contains("K-") && !text.contains("万") && 
                        !text.contains("经验") && !text.contains("学历") &&
                        !text.contains("点击登录") && !text.contains("立即")) {
                        jobInfo.setWorkName(text);
                        log.debug("从宽泛搜索提取到职位名称: {}", text);
                        break;
                    }
                }
            }
            
            if (jobInfo.getWorkName() == null || jobInfo.getWorkName().isEmpty()) {
                log.warn("未找到职位名称: {}", url);
                // 输出更多调试信息
                log.debug("HTML长度: {}", html.length());
                // 检查是否包含登录提示
                if (html.contains("点击登录") || html.contains("立即与BOSS沟通")) {
                    log.warn("页面可能需要登录才能查看完整信息: {}", url);
                }
                // 输出HTML片段帮助调试（仅当DEBUG级别时）
                if (log.isDebugEnabled()) {
                    String htmlSnippet = html.length() > 1000 ? html.substring(0, 1000) : html;
                    log.debug("HTML片段（前1000字符）: {}", htmlSnippet);
                }
            }

            // 提取薪水 - 使用多种选择器
            Element salaryElement = doc.selectFirst("div.name span.salary, .job-primary span.salary, span.salary-text, .salary");
            if (salaryElement != null) {
                jobInfo.setWorkSalary(salaryElement.text().trim());
            }

            // 提取工作地址 - 使用多种选择器
            Element addressElement = doc.selectFirst("div.location-address, .location-address, .job-location, [class*=location]");
            if (addressElement != null) {
                jobInfo.setWorkAddress(addressElement.text().trim());
            }

            // 提取工作内容/职位描述 - 使用多种选择器
            Element contentElement = doc.selectFirst(
                "div.job-sec-text, " +
                ".job-sec-text, " +
                ".job-detail-content, " +
                ".job-detail-text, " +
                ".job-sec, " +
                "[class*=job-sec], " +
                "[class*=job-detail], " +
                ".job-description, " +
                ".description"
            );
            if (contentElement != null) {
                String content = contentElement.text().trim();
                if (!content.isEmpty()) {
                    jobInfo.setWorkContent(content);
                }
            }
            
            // 如果第一次提取失败，尝试从多个位置合并提取
            if (jobInfo.getWorkContent() == null || jobInfo.getWorkContent().isEmpty()) {
                Elements contentElements = doc.select(
                    ".job-sec-text, " +
                    ".job-detail-content, " +
                    "[class*=job-sec], " +
                    "[class*=description]"
                );
                StringBuilder contentBuilder = new StringBuilder();
                for (Element el : contentElements) {
                    String text = el.text().trim();
                    if (text != null && !text.isEmpty() && text.length() > 10) {
                        if (contentBuilder.length() > 0) {
                            contentBuilder.append("\n");
                        }
                        contentBuilder.append(text);
                    }
                }
                if (contentBuilder.length() > 0) {
                    jobInfo.setWorkContent(contentBuilder.toString());
                    log.debug("从多个位置合并提取到工作内容，长度: {}", contentBuilder.length());
                }
            }

            // 提取工作经验要求 - 使用多种选择器
            Element yearElement = doc.selectFirst("p.text-experience, .text-experience, [class*=experience], .job-require span");
            if (yearElement != null) {
                String yearText = yearElement.text().trim();
                // 尝试从多个地方提取经验要求
                if (yearText.isEmpty()) {
                    Elements yearElements = doc.select(".job-primary-info span, .job-detail-header .text");
                    for (Element el : yearElements) {
                        String text = el.text().trim();
                        if (text.contains("经验") || text.contains("年")) {
                            yearText = text;
                            break;
                        }
                    }
                }
                jobInfo.setWorkYear(yearText);
            }

            // 提取学历要求 - 使用多种选择器
            Element graduateElement = doc.selectFirst("p.text-degree, .text-degree, [class*=degree], .job-require span");
            if (graduateElement != null) {
                String degreeText = graduateElement.text().trim();
                // 尝试从多个地方提取学历要求
                if (degreeText.isEmpty()) {
                    Elements degreeElements = doc.select(".job-primary-info span, .job-detail-header .text");
                    for (Element el : degreeElements) {
                        String text = el.text().trim();
                        if (text.contains("学历") || text.contains("大专") || text.contains("本科") || text.contains("硕士")) {
                            degreeText = text;
                            break;
                        }
                    }
                }
                jobInfo.setGraduate(degreeText);
            }

            // 提取HR活跃时间
            Element hrTimeElement = doc.selectFirst("h2.name span, .hr-info span, .hr-active-time, [class*=active]");
            if (hrTimeElement != null) {
                jobInfo.setHrTime(hrTimeElement.text().trim());
            }

            // 提取公司名 - 使用多种选择器确保匹配
            Element companyElement = null;
            String companyName = null;
            
            // 尝试多种选择器
            String[] companySelectors = {
                "a.company-name",
                ".company-name",
                ".company-info a",
                ".company-info .name",
                ".job-header .company-name",
                ".job-detail-header .company-name",
                "div.company-name",
                "h2.company-name",
                "[class*=company-name]",
                "[class*=company][class*=name]",
                ".job-primary .name-info .name",
                ".job-primary-info .name",
                ".company-box .name",
                ".company-wrapper .name",
                "a[href*='/company/']",
                ".info-company a"
            };
            
            // 按顺序尝试选择器
            for (String selector : companySelectors) {
                companyElement = doc.selectFirst(selector);
                if (companyElement != null) {
                    companyName = companyElement.text().trim();
                    if (companyName != null && !companyName.isEmpty() && !companyName.equals("公司")) {
                        break;
                    }
                }
            }
            
            // 如果上述选择器都失败，尝试从页面结构中查找
            if (companyName == null || companyName.isEmpty()) {
                // 查找包含"公司"关键字的链接或文本
                Elements companyLinks = doc.select("a[href*='/company/'], a[href*='company']");
                for (Element link : companyLinks) {
                    String text = link.text().trim();
                    if (text != null && !text.isEmpty() && text.length() < 50) {
                        companyName = text;
                        log.debug("通过链接找到公司名: {}", companyName);
                        break;
                    }
                }
            }
            
            // 如果仍然没有找到，尝试从job-primary区域查找
            if (companyName == null || companyName.isEmpty()) {
                Element jobPrimary = doc.selectFirst(".job-primary, .job-primary-info, .job-header");
                if (jobPrimary != null) {
                    Elements nameElements = jobPrimary.select(".name, h2, h3, [class*=name]");
                    for (Element el : nameElements) {
                        String text = el.text().trim();
                        // 排除职位名称（通常包含岗位关键词）
                        if (text != null && !text.isEmpty() && 
                            !text.equals(jobInfo.getWorkName()) && 
                            text.length() < 50 &&
                            !text.contains("K-") && !text.contains("万")) {
                            companyName = text;
                            log.debug("从job-primary区域找到公司名: {}", companyName);
                            break;
                        }
                    }
                }
            }
            
            if (companyName != null && !companyName.isEmpty()) {
                jobInfo.setCompanyName(companyName);
                log.debug("成功提取公司名: {}", companyName);
            } else {
                log.warn("未找到公司名称: {}", url);
            }
            
            // 验证是否有基本数据（至少要有职位名称）
            if (jobInfo.getWorkName() == null || jobInfo.getWorkName().isEmpty()) {
                log.warn("职位信息不完整（缺少职位名称），跳过保存: {}", url);
                return false;
            }

            // 关键词匹配检查 - 只要匹配关键词即可
            if (!isJobRelevantToQuery(jobInfo)) {
                log.debug("职位与关键词不匹配，跳过保存: 职位名称={}, 关键词={}, URL={}", 
                    jobInfo.getWorkName(), query, url);
                return false;
            }

            // 保存到数据库
            try {
                jobInfoMapper.insert(jobInfo);
                
                // 同时添加到列表（用于CSV备份）
                System.out.println("[保存成功] " + jobInfo.getWorkName() + 
                    (jobInfo.getCompanyName() != null ? " | 公司: " + jobInfo.getCompanyName() : ""));
                
                WorkInfService workInf = new WorkInfService();
                workInf.setUrl(jobInfo.getUrl());
                workInf.setWorkName(jobInfo.getWorkName());
                workInf.setWorkSalary(jobInfo.getWorkSalary());
                workInf.setWorkAddress(jobInfo.getWorkAddress());
                workInf.setWorkContent(jobInfo.getWorkContent());
                workInf.setWorkYear(jobInfo.getWorkYear());
                workInf.setGraduate(jobInfo.getGraduate());
                workInf.setHRTime(jobInfo.getHrTime());
                workInf.setCompanyName(jobInfo.getCompanyName());
                workInfList.add(workInf);
                
                log.info("成功保存职位信息: 职位={}, 公司={}, URL={}", 
                    jobInfo.getWorkName(), jobInfo.getCompanyName(), url);
                return true;
            } catch (Exception e) {
                log.error("保存职位信息到数据库失败: URL={}", url, e);
                return false;
            }

        } catch (Exception e) {
            log.error("处理详情页时出错: URL={}", url, e);
            return false;
        }
    }

    /**
     * 检查职位是否与搜索关键词相关（宽松匹配：只要匹配任一关键词即可）
     * 
     * @param jobInfo 职位信息
     * @return 是否相关
     */
    private boolean isJobRelevantToQuery(JobInfo jobInfo) {
        // 如果没有关键词，则不进行过滤
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        
        String normalizedQuery = query.trim().toLowerCase();
        // 将关键词按空格、逗号、顿号等分隔符分割
        String[] keywords = normalizedQuery.split("[\\s+、,，]+");
        
        // 构建要检查的文本内容（职位名称 + 职位描述）
        StringBuilder content = new StringBuilder();
        
        if (jobInfo.getWorkName() != null) {
            content.append(jobInfo.getWorkName().toLowerCase()).append(" ");
        }
        if (jobInfo.getWorkContent() != null) {
            content.append(jobInfo.getWorkContent().toLowerCase()).append(" ");
        }
        
        String fullContent = content.toString();
        if (fullContent.isEmpty()) {
            // 如果没有任何内容，默认通过
            log.debug("职位内容为空，默认通过关键词检查");
            return true;
        }
        
        // 检查是否至少有一个关键词在职位信息中出现（宽松匹配）
        for (String keyword : keywords) {
            keyword = keyword.trim();
            if (keyword.isEmpty()) {
                continue;
            }
            
            // 如果关键词长度太短（少于1个字符），跳过
            if (keyword.length() < 1) {
                continue;
            }
            
            // 如果关键词在职位名称或描述中找到，即认为匹配
            if (fullContent.contains(keyword)) {
                log.debug("关键词匹配成功: '{}' 在职位信息中找到 (职位: {})", keyword, jobInfo.getWorkName());
                return true; // 只要匹配一个关键词就返回true
            }
        }
        
        // 如果没有匹配到任何关键词，记录详细信息
        log.debug("职位与关键词不匹配: 职位名称='{}', 搜索关键词='{}', 职位内容长度={}", 
            jobInfo.getWorkName(), query, fullContent.length());
        return false;
    }

    /**
     * 检查URL是否在同一域名下
     */
    private boolean isSameDomain(String url) {
        try {
            URI uri = new URI(url);
            return uri.getHost() != null && uri.getHost().equals(domain);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * 保存结果到文件
     */
    private void saveResultsToFile() {
        try (FileWriter writer = new FileWriter("boss_zhipin_results.csv")) {
            // 写入CSV头部（添加工作内容）
            writer.write("URL,工作名称,薪水,工作地址,工作经验,学历要求,公司名称,工作内容\n");

            // 写入数据
            for (WorkInfService workInf : workInfList) {
                writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        workInf.getUrl(),
                        workInf.getWorkName() != null ? workInf.getWorkName() : "",
                        workInf.getWorkSalary() != null ? workInf.getWorkSalary() : "",
                        workInf.getWorkAddress() != null ? workInf.getWorkAddress() : "",
                        workInf.getWorkYear() != null ? workInf.getWorkYear() : "",
                        workInf.getGraduate() != null ? workInf.getGraduate() : "",
                        workInf.getCompanyName() != null ? workInf.getCompanyName() : "",
                        workInf.getWorkContent() != null ? workInf.getWorkContent() : ""));
            }

            System.out.println("结果已保存到 boss_zhipin_results.csv，共 " + workInfList.size() + " 条记录");
        } catch (IOException e) {
            System.err.println("保存文件时出错: " + e.getMessage());
        }
    }

    /**
     * 使用示例（注意：作为Spring Service使用时，需要通过Spring容器注入）
     * 在Spring Boot应用中，可以通过Controller或定时任务来调用
     */
    public static void main(String[] args) {
        // 注意：直接运行main方法时，Mapper无法注入，会报错
        // 建议通过Spring Boot应用启动后，通过Controller或定时任务调用
        System.out.println("请通过Spring Boot应用启动后调用此服务，或使用以下代码：");
        System.out.println("// 示例：在Controller中注入并使用");
        System.out.println("// @Resource private BossZhiPinCrawlerService crawlerService;");
        System.out.println("// crawlerService.initialize(startUrl, query, cityCode, maxPages);");
        System.out.println("// crawlerService.crawl(startUrl);");
        
        // 如果需要独立运行（不使用数据库），可以创建不带@Service注解的独立类
        /*
        String query = "Java"; // 搜索关键词
        String cityCode = "101300600"; // 城市代码（广州）
        String startUrl = "https://www.zhipin.com/web/geek/job?query=" + query + "&city=" + cityCode;
        int maxPages = 500; // 最大爬取页面数

        BossZhiPinCrawlerService crawler = new BossZhiPinCrawlerService(startUrl, query, cityCode, maxPages);
        crawler.crawl(startUrl);
        */
    }
}