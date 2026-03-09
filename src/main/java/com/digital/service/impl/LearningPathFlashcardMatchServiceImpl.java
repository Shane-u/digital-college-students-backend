package com.digital.service.impl;

import com.digital.common.ErrorCode;
import com.digital.exception.BusinessException;
import com.digital.exception.ThrowUtils;
import com.digital.model.dto.learningpath.LearningPathFlashcardMatchRequest;
import com.digital.model.dto.learningpath.LearningPathJson;
import com.digital.model.dto.learningpath.LearningPathNode;
import com.digital.model.vo.LearningPathFlashcardMatchVO;
import com.digital.service.LearningPathFlashcardMatchService;
import com.digital.service.LearningPathService;
import com.digital.service.Neo4jFlashCardService;
import com.huaban.analysis.jieba.JiebaSegmenter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 学习路径节点点击 → 匹配闪卡图谱实现（Neo4j Fulltext + threshold/TopK）
 */
@Service
@Slf4j
public class LearningPathFlashcardMatchServiceImpl implements LearningPathFlashcardMatchService {

    private static final JiebaSegmenter JIEBA = new JiebaSegmenter();

    private static final int DEFAULT_MAX_DESCENDANTS = 200;
    private static final int DEFAULT_MAX_TOKENS = 40;
    private static final int DEFAULT_TOP_K = 100;
    /**
     * 默认得分截断比例：取 max(threshold, maxScore * DEFAULT_RATIO)
     * 提高为 0.6，减小“只有很弱相关性就命中”的误判概率
     */
    private static final double DEFAULT_RATIO = 0.60;

    private static final Set<String> STOP_WORDS = Set.of(
            // 通用学习类弱词
            "学习", "基础", "入门", "了解", "掌握", "进阶", "实战", "概述", "总结", "项目", "练习", "复习", "课程", "模块", "章节",
            // 连接词 / 虚词：避免“io 与 流”“react 与 html”只因为连接词等被误判
            "与", "和", "及", "或", "以及", "还有", "相关", "有关", "介绍"
    );

    private static final Set<Character> LUCENE_SPECIAL_CHARS = Set.of(
            '+', '-', '&', '|', '!', '(', ')', '{', '}', '[', ']', '^', '"', '~', '*', '?', ':', '\\', '/'
    );

    @Resource
    private LearningPathService learningPathService;

    @Resource
    private Neo4jFlashCardService neo4jFlashCardService;

    @Override
    public LearningPathFlashcardMatchVO matchFlashcards(Long userId, String pathId, LearningPathFlashcardMatchRequest request) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(pathId), ErrorCode.PARAMS_ERROR, "路径 ID 不能为空");
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }
        String clickedNodeIdParam = request.getClickedNodeId();
        ThrowUtils.throwIf(StringUtils.isBlank(clickedNodeIdParam),
                ErrorCode.PARAMS_ERROR, "clickedNodeId 不能为空");

        LearningPathFlashcardMatchRequest req = request;

        LearningPathJson json = learningPathService.getPathJson(userId, pathId);
        if (json == null || json.getNodes() == null || json.getNodes().isEmpty()) {
            return null;
        }

        String clickedNodeId = clickedNodeIdParam;

        Map<String, LearningPathNode> nodeMap = new HashMap<>();
        Map<String, List<String>> childrenMap = new HashMap<>();
        for (LearningPathNode n : json.getNodes()) {
            if (n == null || StringUtils.isBlank(n.getNodeId())) {
                continue;
            }
            nodeMap.put(n.getNodeId(), n);
            if (StringUtils.isNotBlank(n.getParentNodeId())) {
                childrenMap.computeIfAbsent(n.getParentNodeId(), k -> new ArrayList<>()).add(n.getNodeId());
            }
        }
        if (!nodeMap.containsKey(clickedNodeId)) {
            ThrowUtils.throwIf(true, ErrorCode.PARAMS_ERROR, "clickedNodeId 不存在于当前学习路径");
        }

        int maxDescendants = req.getMaxDescendants() == null ? DEFAULT_MAX_DESCENDANTS : req.getMaxDescendants();
        int maxTokens = req.getMaxTokens() == null ? DEFAULT_MAX_TOKENS : req.getMaxTokens();
        int topK = req.getTopK() == null ? DEFAULT_TOP_K : req.getTopK();
        double threshold = req.getThreshold() == null ? 0D : req.getThreshold();
        double ratio = req.getRatio() == null ? DEFAULT_RATIO : req.getRatio();

        // 1) 收集“自身 + 全量后代”
        Set<String> collectedNodeIds = collectDescendants(clickedNodeId, childrenMap, Math.max(1, maxDescendants));

        // 2) 生成关键词包（token -> weight）
        Map<String, Integer> tokenWeights = new HashMap<>();
        for (String nid : collectedNodeIds) {
            LearningPathNode n = nodeMap.get(nid);
            if (n == null) continue;

            boolean isClicked = clickedNodeId.equals(nid);
            int nameW = isClicked ? 3 : 2;
            int labelW = isClicked ? 2 : 1;

            addTokens(tokenWeights, n.getName(), nameW);
            addTokens(tokenWeights, n.getLabel(), labelW);
        }

        // Top tokens
        List<Map.Entry<String, Integer>> sorted = tokenWeights.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(e -> e.getKey().length(), Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(Math.max(1, maxTokens))
                .toList();

        List<LearningPathFlashcardMatchVO.Keyword> keywords = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        for (var e : sorted) {
            LearningPathFlashcardMatchVO.Keyword k = new LearningPathFlashcardMatchVO.Keyword();
            k.setToken(e.getKey());
            k.setWeight(e.getValue());
            keywords.add(k);
            tokens.add(e.getKey());
        }

        String luceneQuery = buildLuceneQuery(tokens, tokenWeights);
        if (StringUtils.isBlank(luceneQuery)) {
            LearningPathFlashcardMatchVO vo = new LearningPathFlashcardMatchVO();
            vo.setPathId(pathId);
            vo.setClickedNodeId(clickedNodeId);
            vo.setKeywords(keywords);
            vo.setMatchedFlashcardIds(List.of());
            vo.setScoreMap(Map.of());
            vo.setTopHits(List.of());
            return vo;
        }

        // 3) Neo4j Fulltext 查询
        List<Map<String, Object>> hits = neo4jFlashCardService.fulltextSearchFlashcards(userId, luceneQuery, topK);

        List<LearningPathFlashcardMatchVO.Hit> topHits = new ArrayList<>();
        List<String> matchedIds = new ArrayList<>();
        Map<String, Double> scoreMap = new LinkedHashMap<>();

        double maxScore = 0D;
        for (Map<String, Object> h : hits) {
            Double score = (Double) h.getOrDefault("score", 0D);
            if (score != null && score > maxScore) {
                maxScore = score;
            }
        }
        double cutoff = Math.max(threshold, maxScore * ratio);

        for (Map<String, Object> h : hits) {
            String id = (String) h.get("id");
            String title = (String) h.get("title");
            Double score = (Double) h.getOrDefault("score", 0D);

            LearningPathFlashcardMatchVO.Hit hit = new LearningPathFlashcardMatchVO.Hit();
            hit.setId(id);
            hit.setTitle(title);
            hit.setScore(score);
            if (topHits.size() < 10) {
                topHits.add(hit);
            }

            if (id == null || score == null) continue;
            if (score >= cutoff) {
                matchedIds.add(id);
                scoreMap.put(id, score);
            }
        }

        LearningPathFlashcardMatchVO vo = new LearningPathFlashcardMatchVO();
        vo.setPathId(pathId);
        vo.setClickedNodeId(clickedNodeId);
        vo.setKeywords(keywords);
        vo.setMatchedFlashcardIds(matchedIds);
        vo.setScoreMap(scoreMap);
        vo.setTopHits(topHits);
        return vo;
    }

    private static Set<String> collectDescendants(String clickedNodeId, Map<String, List<String>> childrenMap, int max) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> dq = new ArrayDeque<>();
        dq.add(clickedNodeId);
        while (!dq.isEmpty() && visited.size() < max) {
            String cur = dq.pollFirst();
            if (cur == null || visited.contains(cur)) continue;
            visited.add(cur);
            List<String> children = childrenMap.get(cur);
            if (children != null) {
                for (String c : children) {
                    if (visited.size() >= max) break;
                    dq.addLast(c);
                }
            }
        }
        return visited;
    }

    private static void addTokens(Map<String, Integer> tokenWeights, String text, int weight) {
        String normalized = normalizeText(text);
        if (normalized.isEmpty()) return;

        for (String w : JIEBA.sentenceProcess(normalized)) {
            String t = w == null ? "" : w.trim().toLowerCase();
            if (!isUsefulToken(t)) continue;
            if (STOP_WORDS.contains(t)) continue;
            Integer cur = tokenWeights.get(t);
            tokenWeights.put(t, (cur == null ? 0 : cur) + weight);
        }
        // 短语兜底（保留一份原文）
        if (normalized.length() <= 32 && !STOP_WORDS.contains(normalized)) {
            int w = Math.max(1, weight - 1);
            Integer cur = tokenWeights.get(normalized);
            tokenWeights.put(normalized, (cur == null ? 0 : cur) + w);
        }
    }

    private static String normalizeText(String s) {
        if (s == null) return "";
        String t = s.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[\\p{Punct}，。；：、（）()【】\\[\\]{}<>《》“”\"'`~!@#$%^&*+=|\\\\/]+", " ")
                .replaceAll("\\s+", " ")
                .toLowerCase();
        // 去掉常见连接词，降低“IO 与 流”与“React 与 HTML”只因“与”等词而相似的概率
        t = t.replaceAll("\\s*(与|和|及|或|以及)\\s*", " ");
        t = t.replaceAll("\\([^)]*\\)", " ").replaceAll("（[^）]*）", " ");
        return t.trim();
    }

    private static boolean isUsefulToken(String token) {
        if (token == null) return false;
        String t = token.trim();
        if (t.isEmpty()) return false;
        if (t.matches("[a-z0-9]+")) return true;
        if (t.matches("[\\u4e00-\\u9fa5]+")) return t.length() >= 2;
        return t.length() >= 2;
    }

    private static String escapeLucene(String token) {
        if (token == null) return "";
        StringBuilder sb = new StringBuilder(token.length() * 2);
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (LUCENE_SPECIAL_CHARS.contains(c)) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String buildLuceneQuery(List<String> tokens, Map<String, Integer> tokenWeights) {
        if (tokens == null || tokens.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        for (String t : tokens) {
            if (StringUtils.isBlank(t)) continue;
            String escaped = escapeLucene(t);
            int w = tokenWeights.getOrDefault(t, 1);
            // Lucene boost：token^w
            parts.add(escaped + "^" + Math.max(1, w));
        }
        if (parts.isEmpty()) return "";
        return "(" + String.join(" OR ", parts) + ")";
    }
}

