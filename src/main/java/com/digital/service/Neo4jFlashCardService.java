package com.digital.service;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.Values;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Neo4j 闪卡服务
 * 负责管理用户的 Neo4j 数据库、层级结构和闪卡节点
 *
 * @author Shane
 */
@Service
@Slf4j
public class Neo4jFlashCardService {

    @Resource
    private Driver neo4jDriver;

    @Value("${neo4j.database:}")
    private String defaultDatabase;

    /**
     * 确保用户数据库存在，如果不存在则创建
     * 
     * @param userId 用户ID
     * @throws RuntimeException 如果创建失败
     */
    public void ensureUserDatabaseExists(Long userId) {
        // 使用 userId 作为数据库名，如果配置了默认数据库则使用默认值
        String databaseName = defaultDatabase != null && !defaultDatabase.isEmpty() 
            ? defaultDatabase 
            : String.valueOf(userId);
        
        ensureDatabaseExists(databaseName);
    }

    /**
     * 确保用户数据库存在，如果不存在则创建（内部方法）
     * 
     * @param databaseName 数据库名称
     * @throws RuntimeException 如果创建失败
     */
    private void ensureDatabaseExists(String databaseName) {
        // 如果配置了默认数据库，不需要创建（假设已存在）
        if (defaultDatabase != null && !defaultDatabase.isEmpty() && defaultDatabase.equals(databaseName)) {
            log.debug("使用配置的默认数据库，跳过创建：database={}", databaseName);
            return;
        }

        try (Session systemSession = neo4jDriver.session(SessionConfig.forDatabase("system"))) {
            // 检查数据库是否存在
            String checkQuery = "SHOW DATABASES WHERE name = $dbName";
            var checkResult = systemSession.run(checkQuery, Values.parameters("dbName", databaseName));
            
            boolean exists = checkResult.hasNext();
            
            if (!exists) {
                log.info("数据库不存在，开始创建：database={}", databaseName);
                // 创建数据库（Neo4j 5.x 使用 CREATE DATABASE IF NOT EXISTS）
                // 注意：CREATE DATABASE 不支持参数化，需要直接使用数据库名
                // 这里 databaseName 来自 userId，是安全的
                // 使用反引号包裹数据库名以支持特殊字符
                String createQuery = "CREATE DATABASE `" + databaseName + "` IF NOT EXISTS";
                
                try {
                    var createResult = systemSession.run(createQuery);
                    // 等待创建完成
                    createResult.consume();
                    
                    // 再次检查确认数据库已创建
                    var verifyResult = systemSession.run(checkQuery, Values.parameters("dbName", databaseName));
                    if (verifyResult.hasNext()) {
                        log.info("已成功为用户创建 Neo4j 数据库：database={}", databaseName);
                    } else {
                        log.warn("数据库创建命令执行成功，但验证时未找到数据库：database={}", databaseName);
                        // 等待一小段时间后重试
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("等待数据库创建时被中断: " + databaseName, ie);
                        }
                        verifyResult = systemSession.run(checkQuery, Values.parameters("dbName", databaseName));
                        if (!verifyResult.hasNext()) {
                            throw new RuntimeException("数据库创建失败，验证时未找到数据库: " + databaseName);
                        }
                        log.info("重试验证成功，数据库已创建：database={}", databaseName);
                    }
                } catch (Exception e) {
                    log.error("创建 Neo4j 数据库失败：database={}, error={}", databaseName, e.getMessage(), e);
                    throw new RuntimeException("创建 Neo4j 数据库失败: " + databaseName, e);
                }
            } else {
                log.debug("Neo4j 数据库已存在：database={}", databaseName);
            }
        } catch (RuntimeException e) {
            // 重新抛出运行时异常
            throw e;
        } catch (Exception e) {
            log.error("检查/创建 Neo4j 数据库时发生异常：database={}, error={}", databaseName, e.getMessage(), e);
            throw new RuntimeException("检查/创建 Neo4j 数据库失败: " + databaseName, e);
        }
    }


    /**
     * 保存闪卡到 Neo4j
     * 根据层级标签创建层级结构，并将闪卡节点添加到对应层级
     *
     * @param userId 用户ID
     * @param hierarchyPath 层级路径，如 "根/课程/HTML" 或 "根/课程/前端/HTML"
     * @param flashCardTitle 闪卡标题
     * @param flashCardContent 闪卡内容
     * @param flashCardId 闪卡ID（用于关联）
     */
    public void saveFlashCardToNeo4j(Long userId, String hierarchyPath,
                                     String flashCardTitle, String flashCardContent,
                                     String flashCardId) {
        if (hierarchyPath == null || hierarchyPath.trim().isEmpty()) {
            throw new IllegalArgumentException("层级路径不能为空");
        }

        // 去除路径开头和结尾的斜杠，并解析层级路径
        String normalizedPath = hierarchyPath.trim().replaceAll("^/+|/+$", "");
        String[] levels = Arrays.stream(normalizedPath.split("/"))
                .filter(level -> !level.trim().isEmpty())
                .toArray(String[]::new);
        
        if (levels.length < 2 || levels.length > 4) {
            throw new IllegalArgumentException("层级路径必须是2-4级，格式：根/课程/HTML 或 根/课程/前端/HTML");
        }

        // 验证第一级必须是 "根"
        if (!"根".equals(levels[0])) {
            throw new IllegalArgumentException("层级路径的第一级必须是 '根'");
        }

        // 使用 userId 作为数据库名，如果配置了默认数据库则使用默认值
        String databaseName = defaultDatabase != null && !defaultDatabase.isEmpty() 
            ? defaultDatabase 
            : String.valueOf(userId);
        
        // 确保用户数据库存在
        ensureDatabaseExists(databaseName);
        
        // 使用用户数据库创建会话
        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(databaseName))) {
            try (Transaction tx = session.beginTransaction()) {
                // 创建或获取根节点
                String rootLabel = "User_" + userId + "_Root";
                String rootQuery = "MERGE (root:" + rootLabel + " {name: $rootName, userId: $userId}) RETURN root";
                tx.run(rootQuery, Values.parameters("rootName", levels[0], "userId", userId));

                // 创建层级结构
                String previousLabel = rootLabel;

                for (int i = 1; i < levels.length; i++) {
                    String levelName = levels[i].trim();
                    if (levelName.isEmpty()) {
                        continue;
                    }

                    String currentLabel = "User_" + userId + "_Level" + i;

                    // 创建或获取当前层级节点
                    String createLevelQuery = "MATCH (prev:" + previousLabel + " {name: $prevName, userId: $userId}) " +
                                             "MERGE (curr:" + currentLabel + " {name: $currName, userId: $userId}) " +
                                             "MERGE (prev)-[:LINK_TO]->(curr) RETURN curr";
                    tx.run(createLevelQuery, 
                        Values.parameters("prevName", levels[i-1], "userId", userId, 
                                         "currName", levelName));

                    previousLabel = currentLabel;
                }

                // 在最后一级创建闪卡节点
                String lastLevelLabel = "User_" + userId + "_Level" + (levels.length - 1);
                String lastLevelName = levels[levels.length - 1];
                String flashCardLabel = "User_" + userId + "_FlashCard";

                String createFlashCardQuery = "MATCH (level:" + lastLevelLabel + " {name: $levelName, userId: $userId}) " +
                                             "MERGE (card:" + flashCardLabel + " {id: $flashCardId, title: $title, content: $content, userId: $userId}) " +
                                             "MERGE (level)-[:LINK_TO]->(card) RETURN card";
                tx.run(createFlashCardQuery,
                    Values.parameters("levelName", lastLevelName, "userId", userId,
                                    "flashCardId", flashCardId, "title", flashCardTitle,
                                    "content", flashCardContent));

                tx.commit();
                log.info("闪卡已保存到 Neo4j：userId={}, database={}, hierarchyPath={}, flashCardId={}",
                    userId, databaseName, hierarchyPath, flashCardId);
            }
        } catch (Exception e) {
            log.error("保存闪卡到 Neo4j 失败：userId={}, hierarchyPath={}, error={}",
                userId, hierarchyPath, e.getMessage(), e);
            throw new RuntimeException("保存闪卡到 Neo4j 失败", e);
        }
    }

    /**
     * 更新闪卡在 Neo4j 中的层级路径。
     * 实现方式：
     * 1. 删除该闪卡与旧层级节点之间的 LINK_TO 关系（不删除根节点和其他可能复用的层级节点）
     * 2. 按照新的层级路径重新创建层级结构并关联闪卡
     *
     * 注意：根节点不会被删除。
     */
    public void moveFlashCardToHierarchy(Long userId,
                                         String oldHierarchyPath,
                                         String newHierarchyPath,
                                         String flashCardId,
                                         String flashCardTitle,
                                         String flashCardContent) {
        if (newHierarchyPath == null || newHierarchyPath.trim().isEmpty()) {
            throw new IllegalArgumentException("新层级路径不能为空");
        }

        // 使用 userId 作为数据库名，如果配置了默认数据库则使用默认值
        String databaseName = defaultDatabase != null && !defaultDatabase.isEmpty()
            ? defaultDatabase
            : String.valueOf(userId);

        // 确保用户数据库存在
        ensureDatabaseExists(databaseName);

        // 解析新的层级路径（校验逻辑与 saveFlashCardToNeo4j 保持一致）
        String normalizedNewPath = newHierarchyPath.trim().replaceAll("^/+|/+$", "");
        String[] newLevels = Arrays.stream(normalizedNewPath.split("/"))
                .filter(level -> !level.trim().isEmpty())
                .toArray(String[]::new);

        if (newLevels.length < 2 || newLevels.length > 4) {
            throw new IllegalArgumentException("层级路径必须是2-4级，格式：根/课程/HTML 或 根/课程/前端/HTML");
        }

        if (!"根".equals(newLevels[0])) {
            throw new IllegalArgumentException("层级路径的第一级必须是 '根'");
        }

        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(databaseName))) {
            try (Transaction tx = session.beginTransaction()) {
                String flashCardLabel = "User_" + userId + "_FlashCard";

                // ========= 1. 从旧路径上解绑闪卡，并按规则清理中间层级节点 =========
                if (oldHierarchyPath != null && !oldHierarchyPath.trim().isEmpty()) {
                    String normalizedOldPath = oldHierarchyPath.trim().replaceAll("^/+|/+$", "");
                    String[] oldLevels = Arrays.stream(normalizedOldPath.split("/"))
                            .filter(level -> !level.trim().isEmpty())
                            .toArray(String[]::new);

                    if (oldLevels.length >= 2 && "根".equals(oldLevels[0])) {
                        // 1.1 删除最后一级与闪卡之间的关系
                        String oldLastLevelLabel = "User_" + userId + "_Level" + (oldLevels.length - 1);
                        String oldLastLevelName = oldLevels[oldLevels.length - 1];
                        String detachQuery = "MATCH (level:" + oldLastLevelLabel + " {name: $levelName, userId: $userId})" +
                                             "-[r:LINK_TO]->(card:" + flashCardLabel + " {id: $flashCardId, userId: $userId}) " +
                                             "DELETE r";
                        tx.run(detachQuery, Values.parameters(
                                "levelName", oldLastLevelName,
                                "userId", userId,
                                "flashCardId", flashCardId
                        ));

                        // 1.2 自底向上检查并清理旧路径上的节点（包含最后一级，但不包含根）：
                        //     - 如果某节点已经没有任何子节点（不再 LINK_TO 任何下级节点或闪卡），
                        //       则删除它与父节点之间的关系以及该节点本身。
                        for (int depth = oldLevels.length - 1; depth >= 1; depth--) {
                            String currentLabel = "User_" + userId + "_Level" + depth;
                            String currentName = oldLevels[depth];
                            String parentLabel = depth == 1 ? "User_" + userId + "_Root"
                                                            : "User_" + userId + "_Level" + (depth - 1);
                            String parentName = oldLevels[depth - 1];

                            String cleanupQuery =
                                    "MATCH (parent:" + parentLabel + " {name: $parentName, userId: $userId})" +
                                    "-[pr:LINK_TO]->(curr:" + currentLabel + " {name: $currName, userId: $userId}) " +
                                    "OPTIONAL MATCH (curr)-[cr:LINK_TO]->(child) " +
                                    "WITH parent, curr, pr, count(cr) AS childCount " +
                                    "WHERE childCount = 0 " +
                                    "DETACH DELETE curr";

                            tx.run(cleanupQuery, Values.parameters(
                                    "parentName", parentName,
                                    "currName", currentName,
                                    "userId", userId
                            ));
                        }
                    } else {
                        log.warn("旧层级路径格式不合法或不以 '根' 开头，跳过旧路径清理：userId={}, oldHierarchyPath={}",
                                userId, oldHierarchyPath);
                    }
                } else {
                    // 旧路径未知时，至少保证移除该闪卡与任意层级节点之间的 LINK_TO 关系
                    String genericDetach = "MATCH (card:" + flashCardLabel + " {id: $flashCardId, userId: $userId}) " +
                                           "OPTIONAL MATCH (level)-[r:LINK_TO]->(card) DELETE r";
                    tx.run(genericDetach, Values.parameters("flashCardId", flashCardId, "userId", userId));
                }

                // ========= 2. 按新的路径重新创建层级结构并关联闪卡 =========
                String rootLabel = "User_" + userId + "_Root";
                String rootQuery = "MERGE (root:" + rootLabel + " {name: $rootName, userId: $userId}) RETURN root";
                tx.run(rootQuery, Values.parameters("rootName", newLevels[0], "userId", userId));

                String previousLabel = rootLabel;
                for (int i = 1; i < newLevels.length; i++) {
                    String levelName = newLevels[i].trim();
                    if (levelName.isEmpty()) {
                        continue;
                    }
                    String currentLabel = "User_" + userId + "_Level" + i;
                    String createLevelQuery = "MATCH (prev:" + previousLabel + " {name: $prevName, userId: $userId}) " +
                                             "MERGE (curr:" + currentLabel + " {name: $currName, userId: $userId}) " +
                                             "MERGE (prev)-[:LINK_TO]->(curr) RETURN curr";
                    tx.run(createLevelQuery,
                        Values.parameters("prevName", newLevels[i - 1], "userId", userId,
                                          "currName", levelName));
                    previousLabel = currentLabel;
                }

                String lastLevelLabel = "User_" + userId + "_Level" + (newLevels.length - 1);
                String lastLevelName = newLevels[newLevels.length - 1];

                String createFlashCardQuery = "MATCH (level:" + lastLevelLabel + " {name: $levelName, userId: $userId}) " +
                                             "MERGE (card:" + flashCardLabel + " {id: $flashCardId, userId: $userId}) " +
                                             "SET card.title = $title, card.content = $content " +
                                             "MERGE (level)-[:LINK_TO]->(card) RETURN card";
                tx.run(createFlashCardQuery,
                    Values.parameters("levelName", lastLevelName, "userId", userId,
                                      "flashCardId", flashCardId,
                                      "title", flashCardTitle,
                                      "content", flashCardContent));

                tx.commit();
                log.info("Neo4j 中闪卡层级已更新：userId={}, database={}, oldHierarchyPath={}, newHierarchyPath={}, flashCardId={}",
                    userId, databaseName, oldHierarchyPath, newHierarchyPath, flashCardId);
            }
        } catch (Exception e) {
            log.error("更新 Neo4j 中闪卡层级失败：userId={}, oldHierarchyPath={}, newHierarchyPath={}, flashCardId={}, error={}",
                userId, oldHierarchyPath, newHierarchyPath, flashCardId, e.getMessage(), e);
            throw new RuntimeException("更新 Neo4j 中闪卡层级失败", e);
        }
    }

    public void deleteFlashCardFromNeo4j(Long userId, String flashCardId) {
        // 使用 userId 作为数据库名，如果配置了默认数据库则使用默认值
        String databaseName = defaultDatabase != null && !defaultDatabase.isEmpty() 
            ? defaultDatabase 
            : String.valueOf(userId);
        
        // 确保用户数据库存在
        ensureDatabaseExists(databaseName);
        
        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(databaseName))) {
            try (Transaction tx = session.beginTransaction()) {
                String flashCardLabel = "User_" + userId + "_FlashCard";
                String deleteQuery = "MATCH (card:" + flashCardLabel + " {id: $flashCardId, userId: $userId}) DETACH DELETE card";
                tx.run(deleteQuery, Values.parameters("flashCardId", flashCardId, "userId", userId));
                tx.commit();
                log.info("闪卡已从 Neo4j 删除：userId={}, database={}, flashCardId={}", userId, databaseName, flashCardId);
            }
        } catch (Exception e) {
            log.error("从 Neo4j 删除闪卡失败：userId={}, flashCardId={}, error={}", 
                userId, flashCardId, e.getMessage(), e);
            throw new RuntimeException("从 Neo4j 删除闪卡失败", e);
        }
    }

    /**
     * 从 Neo4j 删除指定层级路径上的节点及其所有关联的子节点和关系。
     *
     * @param userId 用户ID
     * @param hierarchyPath 层级路径，如 "根/课程/HTML"
     */
    /**
     * 更新 Neo4j 中的闪卡节点
     *
     * @param userId 用户ID
     * @param flashCardId 闪卡ID
     * @param newTitle 新标题
     * @param newContent 新内容
     */
    public void updateFlashCardInNeo4j(Long userId, String flashCardId, String newTitle, String newContent) {
        String databaseName = defaultDatabase != null && !defaultDatabase.isEmpty()
            ? defaultDatabase
            : String.valueOf(userId);

        ensureDatabaseExists(databaseName);

        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(databaseName))) {
            try (Transaction tx = session.beginTransaction()) {
                String flashCardLabel = "User_" + userId + "_FlashCard";
                String updateQuery = "MATCH (card:" + flashCardLabel + " {id: $flashCardId, userId: $userId}) " +
                                     "SET card.title = $newTitle, card.content = $newContent " +
                                     "RETURN card";
                tx.run(updateQuery, Values.parameters("flashCardId", flashCardId, "userId", userId,
                                                    "newTitle", newTitle, "newContent", newContent));
                tx.commit();
                log.info("Neo4j 中闪卡已更新：userId={}, database={}, flashCardId={}", userId, databaseName, flashCardId);
            }
        } catch (Exception e) {
            log.error("更新 Neo4j 中闪卡失败：userId={}, flashCardId={}, error={}",
                userId, flashCardId, e.getMessage(), e);
            throw new RuntimeException("更新 Neo4j 中闪卡失败", e);
        }
    }

    public void deleteFlashCardHierarchyFromNeo4j(Long userId, String hierarchyPath) {
        if (hierarchyPath == null || hierarchyPath.trim().isEmpty()) {
            throw new IllegalArgumentException("层级路径不能为空");
        }

        // 去除路径开头和结尾的斜杠，并解析层级路径
        String normalizedPath = hierarchyPath.trim().replaceAll("^/+|/+$", "");
        String[] levels = Arrays.stream(normalizedPath.split("/"))
                .filter(level -> !level.trim().isEmpty())
                .toArray(String[]::new);
        
        if (levels.length < 1) {
            throw new IllegalArgumentException("层级路径至少包含一个层级");
        }

        if (!"根".equals(levels[0])) {
            throw new IllegalArgumentException("层级路径的第一级必须是 '根'");
        }

        // 构建 MATCH 子句来精确匹配目标节点
        StringBuilder pathMatchBuilder = new StringBuilder();
        pathMatchBuilder.append("MATCH (n0:User_").append(userId).append("_Root {name: $name0, userId: $userId})");

        for (int i = 1; i < levels.length; i++) {
            pathMatchBuilder.append("-[:LINK_TO]->(n").append(i).append(":User_").append(userId).append("_Level").append(i).append(" {name: $name").append(i).append(", userId: $userId})");
        }

        String targetNodeVariable = "n" + (levels.length - 1); // 目标节点变量名

        // 构建完整的 Cypher 查询：匹配目标节点，然后 DETACH DELETE 它及其所有下游子节点
        String deleteQuery = pathMatchBuilder.toString() +
                             " OPTIONAL MATCH (" + targetNodeVariable + ")-[*]->(descendant) " +
                             "DETACH DELETE " + targetNodeVariable + ", descendant";

        // 准备参数
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", userId);
        for (int i = 0; i < levels.length; i++) {
            parameters.put("name" + i, levels[i]);
        }

        // 使用 userId 作为数据库名，如果配置了默认数据库则使用默认值
        String databaseName = defaultDatabase != null && !defaultDatabase.isEmpty() 
            ? defaultDatabase 
            : String.valueOf(userId);
        
        // 确保用户数据库存在
        ensureDatabaseExists(databaseName);
        
        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(databaseName))) {
            try (Transaction tx = session.beginTransaction()) {
                tx.run(deleteQuery, parameters);
                tx.commit();
                log.info("闪卡层级已从 Neo4j 删除：userId={}, database={}, hierarchyPath={}", userId, databaseName, hierarchyPath);
            }
        } catch (Exception e) {
            log.error("从 Neo4j 删除闪卡层级失败：userId={}, database={}, hierarchyPath={}, error={}",
                userId, databaseName, hierarchyPath, e.getMessage(), e);
            throw new RuntimeException("从 Neo4j 删除闪卡层级失败", e);
        }
    }
}
