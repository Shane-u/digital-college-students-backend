package com.digital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.digital.mapper.LearningPathFlashcardMatchMapper;
import com.digital.model.dto.learningpath.LearningPathJson;
import com.digital.model.dto.learningpath.LearningPathNode;
import com.digital.model.entity.LearningPathFlashcardMatch;
import com.digital.service.LearningPathLightingService;
import com.digital.service.LearningPathNeo4jService;
import com.digital.service.LearningPathService;
import com.digital.service.Neo4jFlashCardService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * 学习路径点亮回溯实现：
 * - 叶子：关联闪卡全部点亮 => 点亮；testPointsProgress=lit/total
 * - 父节点：直接子节点全部点亮 => 点亮；childrenProgress=lit/total
 */
@Service
@Slf4j
public class LearningPathLightingServiceImpl implements LearningPathLightingService {

    @Resource
    private LearningPathService learningPathService;

    @Resource
    private LearningPathNeo4jService learningPathNeo4jService;

    @Resource
    private LearningPathFlashcardMatchMapper learningPathFlashcardMatchMapper;

    @Resource
    private Neo4jFlashCardService neo4jFlashCardService;

    @Override
    public void recomputePath(Long userId, String pathId) {
        if (userId == null || StringUtils.isBlank(pathId)) {
            return;
        }
        LearningPathJson json = learningPathService.getPathJson(userId, pathId);
        if (json == null || json.getNodes() == null || json.getNodes().isEmpty()) {
            return;
        }

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
        if (nodeMap.isEmpty()) {
            return;
        }

        // 1) 叶子节点：根据 match 表 + 闪卡 litStatus 计算
        Set<String> leafIds = new HashSet<>();
        for (String nodeId : nodeMap.keySet()) {
            List<String> children = childrenMap.get(nodeId);
            if (children == null || children.isEmpty()) {
                leafIds.add(nodeId);
            }
        }

        Map<String, Boolean> isLitMap = new HashMap<>();
        Map<String, String> testPointsProgressMap = new HashMap<>();
        Map<String, String> childrenProgressMap = new HashMap<>();

        if (!leafIds.isEmpty()) {
            for (String leafId : leafIds) {
                List<LearningPathFlashcardMatch> matches = learningPathFlashcardMatchMapper.selectList(
                        new QueryWrapper<LearningPathFlashcardMatch>()
                                .eq("userId", userId)
                                .eq("pathId", pathId)
                                .eq("nodeId", leafId)
                                .eq("isDelete", 0));
                if (matches == null || matches.isEmpty()) {
                    isLitMap.put(leafId, false);
                    testPointsProgressMap.put(leafId, "0/0");
                    childrenProgressMap.put(leafId, "0/0");
                    continue;
                }
                List<String> flashcardIds = matches.stream()
                        .map(LearningPathFlashcardMatch::getFlashcardId)
                        .filter(StringUtils::isNotBlank)
                        .distinct()
                        .toList();
                Map<String, Boolean> litMap = neo4jFlashCardService.getFlashcardLitStatusMap(userId, flashcardIds);
                int total = flashcardIds.size();
                int lit = 0;
                for (String fid : flashcardIds) {
                    if (Boolean.TRUE.equals(litMap.get(fid))) {
                        lit++;
                    }
                }
                boolean leafLit = total > 0 && lit == total;
                isLitMap.put(leafId, leafLit);
                testPointsProgressMap.put(leafId, lit + "/" + total);
                childrenProgressMap.put(leafId, "0/0");
            }
        }

        // 2) 自底向上：父节点是否点亮=直接子节点是否全部点亮
        // 简单做法：重复扫描直到不再变化（节点数有限）
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 2000) {
            changed = false;
            for (String nodeId : nodeMap.keySet()) {
                List<String> children = childrenMap.get(nodeId);
                if (children == null || children.isEmpty()) {
                    continue; // leaf 已计算
                }
                int total = children.size();
                int lit = 0;
                boolean allLit = true;
                for (String c : children) {
                    boolean childLit = Boolean.TRUE.equals(isLitMap.get(c));
                    if (childLit) {
                        lit++;
                    } else {
                        allLit = false;
                    }
                }
                boolean prev = Boolean.TRUE.equals(isLitMap.get(nodeId));
                boolean now = total > 0 && allLit;
                if (prev != now) {
                    isLitMap.put(nodeId, now);
                    changed = true;
                }
                childrenProgressMap.put(nodeId, lit + "/" + total);
                // 非叶子：testPointsProgress 不走闪卡关联，保持 0/0
                testPointsProgressMap.putIfAbsent(nodeId, "0/0");
            }
        }

        // 3) 写回 Neo4j LearningPath 图谱
        for (String nodeId : nodeMap.keySet()) {
            boolean lit = Boolean.TRUE.equals(isLitMap.get(nodeId));
            String tpp = testPointsProgressMap.getOrDefault(nodeId, "0/0");
            String cp = childrenProgressMap.getOrDefault(nodeId, "0/0");
            learningPathNeo4jService.updateNodeLighting(userId, pathId, nodeId, lit, tpp, cp);
        }
    }

    @Override
    public void recomputeByFlashcard(Long userId, String flashcardId) {
        if (userId == null || StringUtils.isBlank(flashcardId)) {
            return;
        }
        // 找出受影响的 pathId，逐个回溯更新
        List<LearningPathFlashcardMatch> matches = learningPathFlashcardMatchMapper.selectList(
                new QueryWrapper<LearningPathFlashcardMatch>()
                        .eq("userId", userId)
                        .eq("flashcardId", flashcardId)
                        .eq("isDelete", 0));
        if (matches == null || matches.isEmpty()) {
            return;
        }
        Set<String> pathIds = new HashSet<>();
        for (LearningPathFlashcardMatch m : matches) {
            if (m != null && StringUtils.isNotBlank(m.getPathId())) {
                pathIds.add(m.getPathId());
            }
        }
        for (String pid : pathIds) {
            try {
                recomputePath(userId, pid);
            } catch (Exception e) {
                log.error("回溯更新学习路径点亮失败: userId={}, pathId={}, error={}",
                        userId, pid, e.getMessage(), e);
            }
        }
    }
}

