package com.digital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.digital.mapper.ContestMapper;
import com.digital.model.dto.contest.ContestQueryRequest;
import com.digital.model.entity.Contest;
import com.digital.model.vo.ContestVO;
import com.digital.service.ContestService;
import com.digital.utils.SqlUtils;
import com.digital.exception.ThrowUtils;
import com.digital.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 竞赛服务实现
 *
 * @author Shane
 */
@Service
@Slf4j
public class ContestServiceImpl extends ServiceImpl<ContestMapper, Contest> implements ContestService {

    private static final String API_BASE_URL = "https://apiv4buffer.saikr.com/api/pc/contest/lists";
    private static final String API_DETAIL_URL = "https://apiv4buffer.saikr.com/api/pc/contest/info";
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ContestServiceImpl() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public int fetchAndSaveContests(Integer page, Integer limit, String classId, Integer level, Integer sort) {
        try {
            // 构建API URL
            String url = buildApiUrl(page, limit, classId, level, sort);
            log.info("开始爬取竞赛数据，URL: {}", url);

            // 发送HTTP请求
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .addHeader("Accept", "application/json")
                    .build();

            int savedCount = 0;
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.error("API请求失败，状态码: {}", response.code());
                    throw new RuntimeException("API请求失败，状态码: " + response.code());
                }

                String responseBody = response.body().string();
                log.debug("API响应: {}", responseBody);

                // 解析JSON响应
                JsonNode rootNode = objectMapper.readTree(responseBody);
                
                // 检查响应码
                if (rootNode.has("code")) {
                    int code = rootNode.get("code").asInt();
                    if (code != 200) {
                        String msg = rootNode.has("msg") ? rootNode.get("msg").asText() : "未知错误";
                        log.error("API返回错误，code: {}, msg: {}", code, msg);
                        throw new RuntimeException("API返回错误: " + msg);
                    }
                }
                
                // 获取data对象
                JsonNode dataNode = rootNode.get("data");
                if (dataNode == null) {
                    log.warn("API响应中没有data对象");
                    return 0;
                }
                
                // 从data中获取list数组
                JsonNode listNode = dataNode.get("list");
                if (listNode == null || !listNode.isArray()) {
                    log.warn("API响应中没有data.list数组");
                    return 0;
                }
                
                // 获取总数（可选，用于日志）
                if (dataNode.has("total")) {
                    int total = dataNode.get("total").asInt();
                    log.info("API返回总数: {}", total);
                }

                // 遍历竞赛数据并保存
                for (JsonNode contestNode : listNode) {
                    try {
                        Contest contest = parseContestFromJson(contestNode);
                        if (contest != null && contest.getContestId() != null) {
                            // 检查是否已存在（根据contestId）
                            Contest existingContest = this.getOne(
                                    new QueryWrapper<Contest>().eq("contestId", contest.getContestId())
                            );

                            if (existingContest != null) {
                                // 更新现有记录
                                contest.setId(existingContest.getId());
                                this.updateById(contest);
                                log.debug("更新竞赛: {}", contest.getContestName());
                            } else {
                                // 插入新记录
                                this.save(contest);
                                savedCount++;
                                log.debug("保存新竞赛: {}", contest.getContestName());
                            }
                        }
                    } catch (Exception e) {
                        log.warn("解析单个竞赛数据失败", e);
                    }
                }

                log.info("成功爬取并保存 {} 条竞赛数据", savedCount);
                return savedCount;
            }
        } catch (IOException e) {
            log.error("爬取竞赛数据时发生IO异常", e);
            throw new RuntimeException("爬取竞赛数据失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("爬取竞赛数据失败", e);
            throw new RuntimeException("爬取竞赛数据失败: " + e.getMessage());
        }
    }

    /**
     * 构建API URL
     */
    private String buildApiUrl(Integer page, Integer limit, String classId, Integer level, Integer sort) {
        StringBuilder url = new StringBuilder(API_BASE_URL);
        url.append("?page=").append(page != null ? page : 1);
        url.append("&limit=").append(limit != null ? limit : 20);

        if (StringUtils.isNotBlank(classId)) {
            // classId可能包含|分隔符，需要URL编码为%7C
            try {
                String encodedClassId = URLEncoder.encode(classId, StandardCharsets.UTF_8.toString());
                url.append("&class_id=").append(encodedClassId);
            } catch (Exception e) {
                url.append("&class_id=").append(classId);
            }
        }

        if (level != null) {
            url.append("&level=").append(level);
        }

        if (sort != null) {
            url.append("&sort=").append(sort);
        }

        return url.toString();
    }

    /**
     * 从JSON节点解析竞赛对象
     */
    private Contest parseContestFromJson(JsonNode contestNode) {
        try {
            Contest contest = new Contest();

            // 解析基本字段
            if (contestNode.has("contest_id")) {
                contest.setContestId(contestNode.get("contest_id").asLong());
            }
            if (contestNode.has("contest_name")) {
                contest.setContestName(contestNode.get("contest_name").asText());
            }
            if (contestNode.has("contest_url")) {
                contest.setContestUrl(contestNode.get("contest_url").asText());
            }
            if (contestNode.has("is_exam")) {
                contest.setIsExam(contestNode.get("is_exam").asInt());
            }
            if (contestNode.has("is_contest_status")) {
                contest.setIsContestStatus(contestNode.get("is_contest_status").asInt());
            }
            if (contestNode.has("regist_start_time")) {
                contest.setRegistStartTime(contestNode.get("regist_start_time").asLong());
            }
            if (contestNode.has("regist_end_time")) {
                contest.setRegistEndTime(contestNode.get("regist_end_time").asLong());
            }
            if (contestNode.has("contest_start_time")) {
                contest.setContestStartTime(contestNode.get("contest_start_time").asLong());
            }
            if (contestNode.has("contest_end_time")) {
                contest.setContestEndTime(contestNode.get("contest_end_time").asLong());
            }
            if (contestNode.has("thumb_pic")) {
                contest.setThumbPic(contestNode.get("thumb_pic").asText());
            }
            if (contestNode.has("level_name")) {
                contest.setLevelName(contestNode.get("level_name").asText());
            }
            if (contestNode.has("organiser")) {
                contest.setOrganiser(contestNode.get("organiser").asText());
            }
            if (contestNode.has("organiser_name")) {
                contest.setOrganiserName(contestNode.get("organiser_name").asText());
            }
            if (contestNode.has("enter_range")) {
                contest.setEnterRange(contestNode.get("enter_range").asText());
            }
            if (contestNode.has("contest_class_first")) {
                JsonNode firstNode = contestNode.get("contest_class_first");
                if (firstNode.isTextual()) {
                    contest.setContestClassFirst(firstNode.asText());
                } else {
                    contest.setContestClassFirst(String.valueOf(firstNode.asLong()));
                }
            }
            if (contestNode.has("contest_class_second")) {
                contest.setContestClassSecond(contestNode.get("contest_class_second").asText());
            }
            if (contestNode.has("contest_class_second_id")) {
                contest.setContestClassSecondId(contestNode.get("contest_class_second_id").asInt());
            }
            if (contestNode.has("time_status")) {
                contest.setTimeStatus(contestNode.get("time_status").asInt());
            }
            if (contestNode.has("time_name")) {
                contest.setTimeName(contestNode.get("time_name").asText());
            }
            if (contestNode.has("rank")) {
                contest.setRank(contestNode.get("rank").asInt());
            }
            if (contestNode.has("is_new")) {
                contest.setIsNew(contestNode.get("is_new").asInt());
            }
            if (contestNode.has("module")) {
                contest.setModule(contestNode.get("module").asInt());
            }

            return contest;
        } catch (Exception e) {
            log.error("解析竞赛JSON数据失败", e);
            return null;
        }
    }

    @Override
    public QueryWrapper<Contest> getQueryWrapper(ContestQueryRequest contestQueryRequest) {
        QueryWrapper<Contest> queryWrapper = new QueryWrapper<>();
        if (contestQueryRequest == null) {
            return queryWrapper;
        }

        String contestName = contestQueryRequest.getContestName();
        String classId = contestQueryRequest.getClassId();
        Integer level = contestQueryRequest.getLevel();
        Integer timeStatus = contestQueryRequest.getTimeStatus();
        String sortField = contestQueryRequest.getSortField();
        String sortOrder = contestQueryRequest.getSortOrder();

        // 竞赛名称模糊查询
        if (StringUtils.isNotBlank(contestName)) {
            queryWrapper.like("contestName", contestName);
        }

        // 分类ID查询（支持多个，用逗号分隔）
        if (StringUtils.isNotBlank(classId)) {
            String[] classIds = classId.split(",");
            if (classIds.length == 1) {
                queryWrapper.eq("contestClassSecondId", Integer.parseInt(classIds[0].trim()));
            } else {
                List<Integer> classIdList = new ArrayList<>();
                for (String id : classIds) {
                    try {
                        classIdList.add(Integer.parseInt(id.trim()));
                    } catch (NumberFormatException e) {
                        log.warn("无效的分类ID: {}", id);
                    }
                }
                if (!classIdList.isEmpty()) {
                    queryWrapper.in("contestClassSecondId", classIdList);
                }
            }
        }

        // 级别查询（level为0时不筛选，其他值转换为中文进行查询）
        if (level != null && level > 0) {
            String[] levelNames = {"不限", "校级", "市级", "省级", "全国性", "全球性", "自由", "其他"};
            if (level < levelNames.length) {
                queryWrapper.eq("levelName", levelNames[level]);
            }
        }
        // level为0或null时不添加级别筛选条件，查询所有级别

        // 时间状态查询
        if (timeStatus != null) {
            queryWrapper.eq("timeStatus", timeStatus);
        }

        // 排序
        if (StringUtils.isNotBlank(sortField)) {
            queryWrapper.orderBy(SqlUtils.validSortField(sortField), 
                    sortOrder.equals("asc"), sortField);
        } else {
            // 默认按创建时间倒序
            queryWrapper.orderByDesc("createTime");
        }

        return queryWrapper;
    }

    @Override
    public ContestVO getContestVO(Contest contest) {
        if (contest == null) {
            return null;
        }
        ContestVO contestVO = new ContestVO();
        BeanUtils.copyProperties(contest, contestVO);
        return contestVO;
    }

    @Override
    public Page<ContestVO> listContestVOByPage(ContestQueryRequest contestQueryRequest) {
        if (contestQueryRequest == null) {
            throw new RuntimeException("查询参数不能为空");
        }
        
        long current = contestQueryRequest.getCurrent();
        long size = contestQueryRequest.getPageSize();
        
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        
        // 构建查询条件
        QueryWrapper<Contest> queryWrapper = this.getQueryWrapper(contestQueryRequest);
        log.debug("查询条件: {}", queryWrapper);
        
        // 执行数据库分页查询（this.page() 会执行SQL查询）
        Page<Contest> contestPage = this.page(new Page<>(current, size), queryWrapper);
        log.info("从数据库查询到 {} 条竞赛记录，总数: {}", contestPage.getRecords().size(), contestPage.getTotal());
        
        // 转换为VO对象
        Page<ContestVO> contestVOPage = new Page<>(current, size, contestPage.getTotal());
        List<ContestVO> contestVOList = contestPage.getRecords().stream()
                .map(this::getContestVO)
                .collect(java.util.stream.Collectors.toList());
        contestVOPage.setRecords(contestVOList);
        
        return contestVOPage;
    }

    @Override
    public Object getContestDetail(Long contestId) {
        try {
            // 从数据库查询竞赛信息
            Contest contest = this.getOne(new QueryWrapper<Contest>().eq("contestId", contestId));
            if (contest == null) {
                throw new RuntimeException("竞赛不存在，contestId: " + contestId);
            }

            // 获取contestUrl并去掉"vse/"前缀
            String contestUrl = contest.getContestUrl();
            if (StringUtils.isBlank(contestUrl)) {
                throw new RuntimeException("竞赛URL为空，contestId: " + contestId);
            }

            // 去掉"vse/"前缀
            String urlParam = contestUrl;
            if (contestUrl.startsWith("vse/")) {
                urlParam = contestUrl.substring(4); // 去掉"vse/"
            }

            // 构建API URL
            String apiUrl = API_DETAIL_URL + "?contest_url=" + URLEncoder.encode(urlParam, StandardCharsets.UTF_8.toString());
            log.info("获取竞赛详情，contestId={}, urlParam={}, apiUrl={}", contestId, urlParam, apiUrl);

            // 发送HTTP请求
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.error("API请求失败，状态码: {}", response.code());
                    throw new RuntimeException("API请求失败，状态码: " + response.code());
                }

                String responseBody = response.body().string();
                log.debug("API响应: {}", responseBody);

                // 解析JSON响应
                JsonNode rootNode = objectMapper.readTree(responseBody);

                // 检查响应码
                if (rootNode.has("code")) {
                    int code = rootNode.get("code").asInt();
                    if (code != 200) {
                        String msg = rootNode.has("msg") ? rootNode.get("msg").asText() : "未知错误";
                        log.error("API返回错误，code: {}, msg: {}", code, msg);
                        throw new RuntimeException("API返回错误: " + msg);
                    }
                }

                // 获取data对象并转换为Map返回（全量返回）
                JsonNode dataNode = rootNode.get("data");
                if (dataNode == null) {
                    log.warn("API响应中没有data对象");
                    throw new RuntimeException("API响应中没有data对象");
                }

                // 将JsonNode转换为Map，这样可以全量返回所有字段
                return objectMapper.convertValue(dataNode, java.util.Map.class);
            }
        } catch (IOException e) {
            log.error("获取竞赛详情时发生IO异常", e);
            throw new RuntimeException("获取竞赛详情失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("获取竞赛详情失败", e);
            throw new RuntimeException("获取竞赛详情失败: " + e.getMessage());
        }
    }
}

