package com.digital.controller;

import com.digital.model.CareerPlanState;
import com.digital.service.DifyWorkflowService;
import com.digital.service.WorkflowRuntime;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * 智能体控制器
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    @Resource
    private DifyWorkflowService difyWorkflowService;
    @Resource
    private WorkflowRuntime runtime;

    @Data
    public static class StartRequest {
        private Long userId;
        private String input;
        private Map<String, Object> assessmentResult; // 职业测评结果
    }

    /**
     * 启动职业规划工作流（使用 Dify）
     * @param req
     * @return
     */
    @PostMapping("/career/start")
    public Map<String, Object> start(@RequestBody StartRequest req) {
        return difyWorkflowService.startWorkflow(
                req.getUserId(),
                req.getInput(),
                req.getAssessmentResult()
        );
    }

    /**
     * 职业规划进度流式接口 - 返回 Flux<ServerSentEvent<String>>
     * @param runId
     * @return
     */
    @GetMapping(value = "/career/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestParam String runId) {
        return Flux.interval(Duration.ofMillis(800))
                .map(t -> {
                    String payload = JsonMapper.builder()
                            .build()
                            .createObjectNode()
                            .putPOJO("events", runtime.list(runId))
                            .get("events")
                            .toString();
                    return ServerSentEvent.builder(payload).event("progress").build();
                });
    }

    /**
     * 职业规划工作流图接口 - 返回工作流描述
     * @return
     */
    @GetMapping(value = "/career/graph", produces = MediaType.TEXT_PLAIN_VALUE)
    public String graph() {
        return difyWorkflowService.getGraph();
    }

    /**
     * 职业规划结果接口 - 返回最终结果 JSON
     * @param runId
     * @return
     */
    @GetMapping("/career/result")
    public Map<String, Object> result(@RequestParam String runId) {
        Optional<CareerPlanState> s = runtime.get(runId);
        return s.<Map<String, Object>>map(state -> {
            java.util.HashMap<String, Object> out = new java.util.HashMap<>();
            if (state.getStatus() != null) out.put("status", state.getStatus());
            if (state.getReportMarkdown() != null) out.put("report", state.getReportMarkdown());
            if (state.getLinks() != null) out.put("links", state.getLinks());
            if (state.getCurrentNode() != null) out.put("currentNode", state.getCurrentNode());
            if (state.getNodeStatus() != null) out.put("nodeStatus", state.getNodeStatus());
            // 确保至少返回状态
            out.putIfAbsent("status", "running");
            return out;
        }).orElse(java.util.Map.of("error", "not found"));
    }

    /**
     * 流式报告接口 - 返回 Flux<ServerSentEvent<String>>
     * 前端可以使用 EventSource 或 fetch with ReadableStream 消费
     */
    @GetMapping(value = "/career/report", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> reportStream(@RequestParam String runId) {
        return Flux.create(sink -> {
            // 定期检查报告是否生成
            Flux.interval(Duration.ofMillis(500))
                    .takeUntil(t -> {
                        Optional<CareerPlanState> state = runtime.get(runId);
                        if (state.isEmpty()) {
                            sink.next(ServerSentEvent.builder("error: 报告不存在").event("error").build());
                            sink.complete();
                            return true;
                        }
                        
                        String status = state.get().getStatus();
                        String report = state.get().getReportMarkdown();
                        
                        // 如果报告已生成，流式发送
                        if ("success".equals(status) && report != null && !report.isEmpty()) {
                            // 按行分块发送报告内容，模拟流式输出
                            String[] lines = report.split("\n");
                            for (String line : lines) {
                                if (!sink.isCancelled()) {
                                    sink.next(ServerSentEvent.builder(line).event("content").build());
                                }
                            }
                            sink.next(ServerSentEvent.builder("[DONE]").event("done").build());
                            sink.complete();
                            return true;
                        }
                        
                        // 如果失败
                        if ("failed".equals(status)) {
                            String error = state.get().getError();
                            sink.next(ServerSentEvent.builder("error: " + (error != null ? error : "报告生成失败")).event("error").build());
                            sink.complete();
                            return true;
                        }
                        
                        return false;
                    })
                    .subscribe(
                            t -> {},
                            error -> sink.error(error),
                            () -> {}
                    );
        });
    }
}


