package com.digital.service.impl;

import com.digital.model.dto.learningpath.LearningPathJson;
import com.digital.model.dto.learningpath.LearningPathNode;
import com.digital.model.vo.LearningPathGraphVO;
import com.digital.service.LearningPathNeo4jService;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 孪孪伴学 - 学习路径 Neo4j 服务实现
 * 使用 LearningPath + LearningNode 标签，属性 userId、pathId、topic 区分
 *
 * @author Shane
 */
@Service
@Slf4j
public class LearningPathNeo4jServiceImpl implements LearningPathNeo4jService {

    @Resource
    private Driver neo4jDriver;

    @Value("${neo4j.learning_path_database:}")
    private String defaultDatabase;

    private String getDatabaseName(Long userId) {
        return (defaultDatabase != null && !defaultDatabase.isEmpty())
                ? defaultDatabase
                : "learningpath" + userId;
    }

    @Override
    public void saveLearningPath(Long userId, String pathId, String topic, LearningPathJson pathJson) {
        if (pathJson == null || pathJson.getNodes() == null || pathJson.getNodes().isEmpty()) {
            log.warn("学习路径节点为空，跳过 Neo4j 保存：userId={}, pathId={}", userId, pathId);
            return;
        }

        String dbName = getDatabaseName(userId);
        ensureDatabaseExists(dbName);

        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(dbName))) {
            session.executeWrite(tx -> {
                // 1. 创建 LearningPath 根节点
                tx.run(
                        "MERGE (lp:LearningPath {userId: $userId, pathId: $pathId}) " +
                                "SET lp.topic = $topic, lp.updatedAt = datetime() " +
                                "RETURN lp",
                        Values.parameters("userId", userId, "pathId", pathId, "topic", topic)
                );

                // 2. 创建所有 LearningNode 节点（根节点 name 用 topic，仅根节点与 lp 建立 HAS_NODE）
                for (LearningPathNode node : pathJson.getNodes()) {
                    boolean isRoot = Boolean.TRUE.equals(node.getIsStart());
                    String nodeName = isRoot ? topic : (node.getName() != null ? node.getName() : "");

                    Map<String, Object> params = new HashMap<>();
                    params.put("userId", userId);
                    params.put("pathId", pathId);
                    params.put("nodeId", node.getNodeId());
                    params.put("label", node.getLabel() != null ? node.getLabel() : "");
                    params.put("isStart", isRoot);
                    params.put("name", nodeName);
                    params.put("testPointsProgress", node.getTestPointsProgress() != null ? node.getTestPointsProgress() : "0/0");
                    params.put("isLit", node.getIsLit() != null ? node.getIsLit() : false);
                    params.put("litTime", node.getLitTime());
                    params.put("childrenProgress", node.getChildrenProgress() != null ? node.getChildrenProgress() : "0/0");
                    params.put("createdAt", node.getCreatedAt());

                    tx.run(
                            "MATCH (lp:LearningPath {userId: $userId, pathId: $pathId}) " +
                                    "MERGE (n:LearningNode {userId: $userId, pathId: $pathId, nodeId: $nodeId}) " +
                                    "SET n.label = $label, n.isStart = $isStart, n.name = $name, " +
                                    "    n.testPointsProgress = $testPointsProgress, n.isLit = $isLit, " +
                                    "    n.litTime = $litTime, n.childrenProgress = $childrenProgress, n.createdAt = $createdAt " +
                                    "RETURN n",
                            params
                    );
                    if (isRoot) {
                        tx.run(
                                "MATCH (lp:LearningPath {userId: $userId, pathId: $pathId}) " +
                                        "MATCH (n:LearningNode {userId: $userId, pathId: $pathId, nodeId: $nodeId}) " +
                                        "MERGE (lp)-[:HAS_NODE]->(n) RETURN n",
                                Values.parameters("userId", userId, "pathId", pathId, "nodeId", node.getNodeId())
                        );
                    }
                }

                // 3. 创建 PARENT_OF 关系
                for (LearningPathNode node : pathJson.getNodes()) {
                    String parentId = node.getParentNodeId();
                    if (parentId != null && !parentId.trim().isEmpty()) {
                        tx.run(
                                "MATCH (parent:LearningNode {userId: $userId, pathId: $pathId, nodeId: $parentId}) " +
                                        "MATCH (child:LearningNode {userId: $userId, pathId: $pathId, nodeId: $childId}) " +
                                        "MERGE (parent)-[:PARENT_OF]->(child) " +
                                        "RETURN parent, child",
                                Values.parameters("userId", userId, "pathId", pathId,
                                        "parentId", parentId, "childId", node.getNodeId())
                        );
                    }
                }

                return null;
            });
            log.info("学习路径已保存到 Neo4j：userId={}, pathId={}, topic={}", userId, pathId, topic);
        } catch (Exception e) {
            log.error("保存学习路径到 Neo4j 失败：userId={}, pathId={}, error={}", userId, pathId, e.getMessage(), e);
            throw new RuntimeException("保存学习路径到 Neo4j 失败", e);
        }
    }

    @Override
    public void updateLearningPath(Long userId, String pathId, String topic, LearningPathJson pathJson) {
        deleteLearningPath(userId, pathId);
        saveLearningPath(userId, pathId, topic, pathJson);
    }

    @Override
    public void deleteLearningPath(Long userId, String pathId) {
        String dbName = getDatabaseName(userId);
        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(dbName))) {
            session.executeWrite(tx -> {
                tx.run(
                        "MATCH (lp:LearningPath {userId: $userId, pathId: $pathId}) " +
                                "OPTIONAL MATCH (n:LearningNode {userId: $userId, pathId: $pathId}) " +
                                "DETACH DELETE lp, n",
                        Values.parameters("userId", userId, "pathId", pathId)
                );
                return null;
            });
            log.info("学习路径已从 Neo4j 删除：userId={}, pathId={}", userId, pathId);
        } catch (Exception e) {
            log.error("从 Neo4j 删除学习路径失败：userId={}, pathId={}, error={}", userId, pathId, e.getMessage(), e);
            throw new RuntimeException("从 Neo4j 删除学习路径失败", e);
        }
    }

    @Override
    public LearningPathJson getLearningPath(Long userId, String pathId) {
        String dbName = getDatabaseName(userId);
        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(dbName))) {
            var result = session.run(
                    "MATCH (n:LearningNode {userId: $userId, pathId: $pathId}) " +
                            "OPTIONAL MATCH (parent:LearningNode)-[:PARENT_OF]->(n) " +
                            "RETURN n, parent.nodeId AS parentNodeId " +
                            "ORDER BY n.nodeId",
                    Values.parameters("userId", userId, "pathId", pathId)
            );

            List<LearningPathNode> nodes = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                var nodeProps = record.get("n").asNode();
                Map<String, Object> props = nodeProps.asMap();

                LearningPathNode node = new LearningPathNode();
                node.setNodeId(getString(props, "nodeId"));
                node.setLabel(getString(props, "label"));
                node.setIsStart(getBoolean(props, "isStart"));
                node.setParentNodeId(record.get("parentNodeId").isNull() ? null : record.get("parentNodeId").asString());
                node.setName(getString(props, "name"));
                node.setTestPointsProgress(getString(props, "testPointsProgress"));
                node.setIsLit(getBoolean(props, "isLit"));
                node.setLitTime(getString(props, "litTime"));
                node.setChildrenProgress(getString(props, "childrenProgress"));
                node.setCreatedAt(getString(props, "createdAt"));
                nodes.add(node);
            }

            if (nodes.isEmpty()) {
                return null;
            }

            LearningPathJson json = new LearningPathJson();
            json.setNodes(nodes);
            return json;
        } catch (Exception e) {
            log.error("从 Neo4j 获取学习路径失败：userId={}, pathId={}, error={}", userId, pathId, e.getMessage(), e);
            throw new RuntimeException("从 Neo4j 获取学习路径失败", e);
        }
    }

    @Override
    public List<String> listPathIds(Long userId) {
        String dbName = getDatabaseName(userId);
        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(dbName))) {
            var result = session.run(
                    "MATCH (lp:LearningPath {userId: $userId}) RETURN lp.pathId AS pathId",
                    Values.parameters("userId", userId)
            );

            List<String> pathIds = new ArrayList<>();
            while (result.hasNext()) {
                pathIds.add(result.next().get("pathId").asString());
            }
            return pathIds;
        } catch (Exception e) {
            log.error("从 Neo4j 列出学习路径失败：userId={}, error={}", userId, e.getMessage(), e);
            throw new RuntimeException("从 Neo4j 列出学习路径失败", e);
        }
    }

    @Override
    public LearningPathGraphVO getLearningPathGraph(Long userId, String pathId) {
        LearningPathJson json = getLearningPath(userId, pathId);
        if (json == null || json.getNodes() == null) {
            return null;
        }

        String dbName = getDatabaseName(userId);
        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(dbName))) {
            LearningPathGraphVO vo = new LearningPathGraphVO();
            vo.setPathId(pathId);
            vo.setNodes(json.getNodes());

            List<LearningPathGraphVO.Relationship> rels = new ArrayList<>();

            // HAS_NODE: pathId -> root
            var hasNodeResult = session.run(
                    "MATCH (lp:LearningPath {userId: $userId, pathId: $pathId})-[:HAS_NODE]->(root:LearningNode) RETURN root.nodeId AS rootId",
                    Values.parameters("userId", userId, "pathId", pathId)
            );
            if (hasNodeResult.hasNext()) {
                String rootId = hasNodeResult.next().get("rootId").asString();
                LearningPathGraphVO.Relationship r = new LearningPathGraphVO.Relationship();
                r.setSourceNodeId(pathId);
                r.setTargetNodeId(rootId);
                r.setType("HAS_NODE");
                rels.add(r);
            }

            // PARENT_OF
            for (LearningPathNode node : json.getNodes()) {
                String parentId = node.getParentNodeId();
                if (parentId != null && !parentId.isEmpty()) {
                    LearningPathGraphVO.Relationship r = new LearningPathGraphVO.Relationship();
                    r.setSourceNodeId(parentId);
                    r.setTargetNodeId(node.getNodeId());
                    r.setType("PARENT_OF");
                    rels.add(r);
                }
            }

            vo.setRelationships(rels);

            // topic 从根节点 name 取
            json.getNodes().stream()
                    .filter(n -> Boolean.TRUE.equals(n.getIsStart()))
                    .findFirst()
                    .ifPresent(root -> vo.setTopic(root.getName()));

            return vo;
        } catch (Exception e) {
            log.error("获取学习路径图谱失败：userId={}, pathId={}, error={}", userId, pathId, e.getMessage(), e);
            throw new RuntimeException("获取学习路径图谱失败", e);
        }
    }

    private void ensureDatabaseExists(String databaseName) {
        if (defaultDatabase != null && !defaultDatabase.isEmpty() && defaultDatabase.equals(databaseName)) {
            return;
        }
        try (Session systemSession = neo4jDriver.session(SessionConfig.forDatabase("system"))) {
            var checkResult = systemSession.run("SHOW DATABASES WHERE name = $dbName",
                    Values.parameters("dbName", databaseName));
            if (!checkResult.hasNext()) {
                systemSession.run("CREATE DATABASE `" + databaseName + "` IF NOT EXISTS").consume();
            }
        }
    }

    private static String getString(Map<String, Object> props, String key) {
        Object v = props.get(key);
        return v == null ? null : v.toString();
    }

    private static Boolean getBoolean(Map<String, Object> props, String key) {
        Object v = props.get(key);
        if (v == null) return false;
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(v.toString());
    }
}
