package com.digital.service;

import com.digital.model.entity.Competition;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 竞赛信息解析服务
 */
@Service
@Slf4j
public class CompetitionService {

    private final OkHttpClient httpClient;
    private static final String SAIKR_URL = "https://www.saikr.com/index/hot/contest";

    public CompetitionService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 获取赛氪网最新竞赛TOP10
     *
     * @return 竞赛列表
     */
    public List<Competition> getLatestCompetitions() {
        try {
            // 从赛氪网获取HTML内容
            String html = fetchHtmlFromSaikr();
            if (html == null || html.isEmpty()) {
                log.error("获取赛氪网HTML内容失败");
                return new ArrayList<>();
            }

            // 解析HTML并提取竞赛信息
            return parseCompetitionsFromHtml(html);
        } catch (Exception e) {
            log.error("获取竞赛信息失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 从赛氪网获取HTML内容
     *
     * @return HTML字符串
     */
    private String fetchHtmlFromSaikr() {
        try {
            Request request = new Request.Builder()
                    .url(SAIKR_URL)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().string();
                } else {
                    log.error("HTTP请求失败，状态码: {}", response.code());
                    return null;
                }
            }
        } catch (IOException e) {
            log.error("获取赛氪网HTML内容时发生IO异常", e);
            return null;
        }
    }

    /**
     * 从HTML中解析竞赛信息
     *
     * @param html HTML内容
     * @return 竞赛列表
     */
    private List<Competition> parseCompetitionsFromHtml(String html) {
        List<Competition> competitions = new ArrayList<>();

        try {
            Document doc = Jsoup.parse(html);

            Elements items = doc.select("ul.sk-ranklist > li.item");
            if (items.isEmpty()) {
                log.warn("未找到竞赛列表 ul.sk-ranklist > li.item");
                return competitions;
            }

            int limit = Math.min(10, items.size());
            log.info("找到 {} 条竞赛条目，取前 {} 条", items.size(), limit);

            for (int i = 0; i < limit; i++) {
                try {
                    Competition competition = parseCompetitionFromListItem(items.get(i), i + 1);
                    if (competition != null) {
                        competitions.add(competition);
                    }
                } catch (Exception e) {
                    log.warn("解析单个竞赛信息失败", e);
                }
            }

            log.info("成功解析 {} 个竞赛信息", competitions.size());
        } catch (Exception e) {
            log.error("解析HTML时发生异常", e);
        }

        return competitions;
    }

    private Competition parseCompetitionFromListItem(Element li, int rank) {
        Element titleLink = li.selectFirst("div.list-info > a");
        if (titleLink == null) {
            log.warn("竞赛条目缺少标题链接 div.list-info > a");
            return null;
        }

        String href = titleLink.attr("href");
        if (href == null || href.isEmpty()) {
            return null;
        }

        String name = titleLink.text().trim();
        String url = normalizeSaikrUrl(href);
        String popularity = extractViewCount(li);

        return new Competition(rank, name, popularity, url);
    }

    /**
     * 底部「浏览」量文案去掉「浏览」，仅保留如 19.0万
     */
    private String extractViewCount(Element li) {
        Element viewSpan = li.selectFirst("div.btm-info span.mr20");
        if (viewSpan == null) {
            return "";
        }
        String raw = viewSpan.text().replace('\u00A0', ' ').trim();
        return raw.replace("浏览", "").trim();
    }

    private String normalizeSaikrUrl(String href) {
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }
        if (href.startsWith("//")) {
            return "https:" + href;
        }
        if (href.startsWith("/")) {
            return "https://www.saikr.com" + href;
        }
        return "https://www.saikr.com/" + href;
    }
}
