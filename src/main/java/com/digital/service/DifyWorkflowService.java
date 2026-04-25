package com.digital.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.digital.model.CareerPlanState;
import com.digital.model.entity.CareerPlanReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dify 工作流服务
 * 用于替换原有的 CareerPlanWorkflow
 */
@Slf4j
@Service
public class DifyWorkflowService {
    
    @Resource
    private DifyClient difyClient;
    
    @Resource
    private WorkflowRuntime runtime;

    @Resource
    private CareerPlanReportService careerPlanReportService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 启动工作流（异步执行）
     */
    public Map<String, Object> startWorkflow(Long userId, String userInput, Map<String, Object> assessmentResult) {
        String runId = UUID.randomUUID().toString();
        
        // 初始化状态
        CareerPlanState state = new CareerPlanState();
        state.setUserId(userId);
        state.setUserInput(userInput);
        state.setAssessmentResult(assessmentResult != null ? assessmentResult : new HashMap<>());
        state.setRunId(runId);
        state.setStatus("running");
        
        // 初始化节点状态
        Map<String, String> nodeStatus = new HashMap<>();
        nodeStatus.put("dify_workflow", "运行中");
        state.setNodeStatus(nodeStatus);
        state.setCurrentNode("dify_workflow");
        
        runtime.put(runId, state);
        runtime.appendEvent(runId, "dify_workflow", "start", "正在启动 Dify 工作流");
        
        // 构建输入参数
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("user_id", userId != null ? userId.toString() : "");
        inputs.put("user_input", userInput != null ? userInput : "");
        if (assessmentResult != null && !assessmentResult.isEmpty()) {
            // 需要序列化为 JSON 字符串
            try {
                String assessmentResultJson = objectMapper.writeValueAsString(assessmentResult);
                inputs.put("assessment_result", assessmentResultJson);
            } catch (Exception e) {
                log.warn("Failed to serialize assessment_result to JSON", e);
                // 如果序列化失败，使用空字符串
                inputs.put("assessment_result", "");
            }
        } else {
            // 如果没有测评结果，传递空字符串
            inputs.put("assessment_result", "");
        }
        
        // 异步执行工作流
        String user = userId != null ? userId.toString() : "anonymous";
        log.info("开始执行 Dify 工作流，runId: {}, userId: {}, inputs: {}", runId, user, inputs);
        
        new Thread(() -> {
            try {
                // 使用流式模式执行
                log.info("调用 Dify API，runId: {}", runId);
                Response response = difyClient.runWorkflowStreaming(user, inputs);
                
                log.info("收到 Dify API 响应，runId: {}, statusCode: {}, headers: {}", 
                        runId, response.code(), response.headers());
                
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    log.error("Dify API 调用失败，runId: {}, code: {}, error: {}", runId, response.code(), errorBody);
                    throw new IOException("Dify workflow execution failed: " + response.code() + " - " + errorBody);
                }
                
                // 检查响应类型
                String contentType = response.header("Content-Type");
                log.info("响应 Content-Type: {}, runId: {}", contentType, runId);
                
                if (contentType != null && contentType.contains("text/event-stream")) {
                    log.info("开始处理流式响应，runId: {}", runId);
                    // 处理流式响应
                    processStreamingResponse(runId, response);
                } else {
                    log.warn("响应不是流式格式，runId: {}, Content-Type: {}", runId, contentType);
                    // 如果不是流式响应，尝试读取响应体
                    String responseBody = response.body() != null ? response.body().string() : "";
                    log.info("非流式响应内容，runId: {}, body: {}", runId, responseBody);
                    runtime.appendEvent(runId, "dify_workflow", "non_streaming", "收到非流式响应");
                }
                
            } catch (Exception e) {
                log.error("Dify workflow execution failed", e);
                runtime.get(runId).ifPresent(s -> {
                    s.setStatus("failed");
                    s.setError(e.getMessage());
                });
                runtime.appendEvent(runId, "dify_workflow", "error", "执行失败: " + e.getMessage());
            }
        }).start();
        
        return Map.of(
                "runId", runId,
                "sse", "/agent/career/stream?runId=" + runId,
                "report", "/agent/career/report?runId=" + runId,
                "graph", "/agent/career/graph"
        );
    }
    
    /**
     * 处理流式响应
     */
    private void processStreamingResponse(String runId, Response response) throws IOException {
        ResponseBody body = response.body();
        if (body == null) {
            throw new IOException("Response body is null");
        }
        
        StringBuilder reportBuilder = new StringBuilder();
        log.info("开始处理 Dify 流式响应，runId: {}", runId);
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
            
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                // 跳过空行
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                if (line.startsWith("data: ")) {
                    String jsonData = line.substring(6); // 移除 "data: " 前缀
                    
                    // 跳过空数据
                    if (jsonData.trim().isEmpty()) {
                        continue;
                    }
                    
                    log.debug("收到 SSE 事件，runId: {}, line: {}, data: {}", runId, lineCount, jsonData);
                    
                    try {
                        JsonNode eventNode = objectMapper.readTree(jsonData);
                        String event = eventNode.path("event").asText();
                        
                        if (event == null || event.isEmpty()) {
                            log.warn("事件类型为空，跳过处理，runId: {}, jsonData: {}", runId, jsonData);
                            continue;
                        }
                        
                        log.info("处理事件类型: {}, runId: {}", event, runId);
                        
                        // 处理不同的事件类型
                        switch (event) {
                            case "workflow_started":
                                log.info("工作流已启动，runId: {}", runId);
                                runtime.appendEvent(runId, "dify_workflow", "started", "工作流已启动");
                                runtime.get(runId).ifPresent(s -> {
                                    s.setCurrentNode("dify_workflow");
                                    if (s.getNodeStatus() == null) {
                                        s.setNodeStatus(new HashMap<>());
                                    }
                                    s.getNodeStatus().put("dify_workflow", "运行中");
                                });
                                break;
                                
                            case "node_started":
                                JsonNode nodeData = eventNode.path("data");
                                String nodeTitle = nodeData.path("title").asText("节点");
                                String nodeId = nodeData.path("node_id").asText("");
                                log.info("节点开始执行，runId: {}, nodeId: {}, title: {}", runId, nodeId, nodeTitle);
                                runtime.appendEvent(runId, "dify_workflow", "node_started", "节点开始执行: " + nodeTitle);
                                runtime.get(runId).ifPresent(s -> {
                                    s.setCurrentNode(nodeTitle);
                                    if (s.getNodeStatus() == null) {
                                        s.setNodeStatus(new HashMap<>());
                                    }
                                    s.getNodeStatus().put(nodeTitle, "运行中");
                                });
                                break;
                                
                            case "text_chunk":
                                JsonNode textData = eventNode.path("data");
                                String text = textData.path("text").asText("");
                                if (!text.isEmpty()) {
                                    reportBuilder.append(text);
                                    runtime.appendEvent(runId, "dify_workflow", "text_chunk", text);
                                }
                                break;
                                
                            case "node_finished":
                                JsonNode nodeFinishData = eventNode.path("data");
                                String nodeTitleFinish = nodeFinishData.path("title").asText("节点");
                                String nodeStatus = nodeFinishData.path("status").asText();
                                log.info("节点执行完成，runId: {}, node: {}, status: {}", runId, nodeTitleFinish, nodeStatus);
                                runtime.appendEvent(runId, "dify_workflow", "node_finished", 
                                        "节点执行完成: " + nodeTitleFinish + " (" + nodeStatus + ")");
                                runtime.get(runId).ifPresent(s -> {
                                    if (s.getNodeStatus() == null) {
                                        s.setNodeStatus(new HashMap<>());
                                    }
                                    s.getNodeStatus().put(nodeTitleFinish, "已完成");
                                });
                                break;
                                
                            case "workflow_finished":
                                JsonNode finishData = eventNode.path("data");
                                String status = finishData.path("status").asText();
                                JsonNode outputs = finishData.path("outputs");
                                log.info("工作流执行完成，runId: {}, status: {}", runId, status);
                                
                                // 更新最终状态
                                runtime.get(runId).ifPresent(s -> {
                                    s.setStatus("success".equals(status) ? "success" : "failed");
                                    if (s.getNodeStatus() == null) {
                                        s.setNodeStatus(new HashMap<>());
                                    }
                                    s.getNodeStatus().put("dify_workflow", "已完成");
                                    if (outputs != null && !outputs.isNull()) {
                                        // 尝试提取报告内容
                                        if (outputs.has("report") || outputs.has("text") || outputs.has("output")) {
                                            String report = outputs.path("report").asText("");
                                            if (report.isEmpty()) {
                                                report = outputs.path("text").asText("");
                                            }
                                            if (report.isEmpty()) {
                                                report = outputs.path("output").asText("");
                                            }
                                            if (!report.isEmpty()) {
                                                s.setReportMarkdown(report);
                                            } else if (reportBuilder.length() > 0) {
                                                s.setReportMarkdown(reportBuilder.toString());
                                            }
                                        } else if (reportBuilder.length() > 0) {
                                            s.setReportMarkdown(reportBuilder.toString());
                                        }
                                        
                                        // 提取其他输出字段
                                        if (outputs.has("links")) {
                                            try {
                                                JsonNode linksNode = outputs.path("links");
                                                if (linksNode.isArray()) {
                                                    java.util.List<String> links = new java.util.ArrayList<>();
                                                    for (JsonNode linkNode : linksNode) {
                                                        links.add(linkNode.asText());
                                                    }
                                                    s.setLinks(links);
                                                }
                                            } catch (Exception e) {
                                                log.warn("Failed to parse links", e);
                                            }
                                        }
                                    } else if (reportBuilder.length() > 0) {
                                        s.setReportMarkdown(reportBuilder.toString());
                                    }
                                    
                                    if ("failed".equals(status)) {
                                        s.setError(finishData.path("error").asText("工作流执行失败"));
                                    }
                                });
                                
                                runtime.appendEvent(runId, "dify_workflow", "finished", 
                                        "工作流执行完成: " + status);

                                // 报告自动落库（成功/失败都记录，便于历史追溯）
                                try {
                                    persistReport(runId);
                                } catch (Exception e) {
                                    log.warn("职业规划报告落库失败，runId: {}", runId, e);
                                }
                                break;
                                
                            case "ping":
                                log.debug("收到心跳事件，runId: {}", runId);
                                break;
                                
                            default:
                                log.warn("未知事件类型: {}, runId: {}, jsonData: {}", event, runId, jsonData);
                        }
                    } catch (Exception e) {
                        log.error("解析 SSE 事件失败，runId: {}, jsonData: {}", runId, jsonData, e);
                        runtime.appendEvent(runId, "dify_workflow", "parse_error", "解析事件失败: " + e.getMessage());
                    }
                } else {
                    // 记录非 data: 开头的行，用于调试
                    log.debug("收到非 SSE 格式的行，runId: {}, line: {}", runId, line);
                }
            }
            
            log.info("流式响应处理完成，runId: {}, 总行数: {}", runId, lineCount);
        } catch (Exception e) {
            log.error("处理流式响应时发生异常，runId: {}", runId, e);
            runtime.get(runId).ifPresent(s -> {
                s.setStatus("failed");
                s.setError("流式响应处理失败: " + e.getMessage());
            });
            runtime.appendEvent(runId, "dify_workflow", "error", "流式响应处理失败: " + e.getMessage());
            throw e;
        }
        
        // 如果工作流已完成但没有设置报告，使用累积的文本
        runtime.get(runId).ifPresent(s -> {
            if (s.getReportMarkdown() == null || s.getReportMarkdown().isEmpty()) {
                if (reportBuilder.length() > 0) {
                    s.setReportMarkdown(reportBuilder.toString());
                } else {
                    s.setReportMarkdown("# 职业规划报告\n\n工作流执行完成，但未生成报告内容。");
                }
            }
        });
    }

    /**
     * 将当前 runId 的最终状态与报告内容落库到 career_plan_report
     */
    private void persistReport(String runId) {
        runtime.get(runId).ifPresent(state -> {
            CareerPlanReport report = new CareerPlanReport();
            report.setUserId(state.getUserId());
            report.setRunId(runId);
            report.setReportMarkdown(state.getReportMarkdown());
            report.setError(state.getError());

            // 同一 userId + runId 存在则更新
            QueryWrapper<CareerPlanReport> qw = new QueryWrapper<>();
            qw.eq("userId", state.getUserId());
            qw.eq("runId", runId);
            qw.last("limit 1");
            CareerPlanReport exist = careerPlanReportService.getOne(qw);
            if (exist != null) {
                report.setId(exist.getId());
                careerPlanReportService.updateById(report);
            } else {
                careerPlanReportService.save(report);
            }
        });
    }
    
    /**
     * 获取工作流图（返回空字符串，因为 Dify 工作流图不在本地）
     */
    public String getGraph() {
        return "# Dify Workflow\n\n工作流由 Dify 平台管理，请访问 Dify 控制台查看工作流图。";
    }
}

