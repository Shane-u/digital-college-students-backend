package com.digital.service;

import com.digital.model.CareerPlanState;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WorkflowRuntime {

    private final Map<String, CareerPlanState> store = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> events = new ConcurrentHashMap<>();

    @Data
    public static class RunHandle {
        private String runId;
        private String sseUrl;
    }

    public void put(String runId, CareerPlanState state) {
        store.put(runId, state);
    }

    public Optional<CareerPlanState> get(String runId) {
        return Optional.ofNullable(store.get(runId));
    }

    // 事件状态流转记录
    public void appendEvent(String runId, String node, String status, String message) {
        events.computeIfAbsent(runId, k -> new ArrayList<>())
                .add(Map.of(
                        "ts", System.currentTimeMillis(),
                        "node", node,
                        "status", status,
                        "message", message
                ));
    }

    public List<Map<String, Object>> list(String runId) {
        return events.getOrDefault(runId, List.of());
    }
}