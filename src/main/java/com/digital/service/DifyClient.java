package com.digital.service;

import com.digital.config.DifyConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Dify API 客户端
 */
@Slf4j
@Service
public class DifyClient {
    
    @Resource
    private DifyConfig difyConfig;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient;
    
    public DifyClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * 执行工作流（阻塞模式）
     */
    public WorkflowResponse runWorkflowBlocking(String user, Map<String, Object> inputs) throws IOException {
        String url = difyConfig.getBaseUrl() + "/workflows/run";
        if (difyConfig.getWorkflowId() != null && !difyConfig.getWorkflowId().isEmpty()) {
            url = difyConfig.getBaseUrl() + "/workflows/" + difyConfig.getWorkflowId() + "/run";
        }
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("inputs", inputs != null ? inputs : new HashMap<>());
        requestBody.put("response_mode", "blocking");
        requestBody.put("user", user);
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8")))
                .addHeader("Authorization", "Bearer " + difyConfig.getApiKey())
                .addHeader("Content-Type", "application/json")
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Dify API request failed: " + response.code() + " - " + errorBody);
            }
            
            String responseBody = response.body() != null ? response.body().string() : "{}";
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            
            WorkflowResponse workflowResponse = new WorkflowResponse();
            workflowResponse.setWorkflowRunId(jsonNode.path("workflow_run_id").asText());
            workflowResponse.setTaskId(jsonNode.path("task_id").asText());
            
            JsonNode dataNode = jsonNode.path("data");
            workflowResponse.setStatus(dataNode.path("status").asText());
            workflowResponse.setOutputs(dataNode.path("outputs"));
            workflowResponse.setError(dataNode.path("error").asText(null));
            workflowResponse.setElapsedTime(dataNode.path("elapsed_time").asDouble(0));
            workflowResponse.setTotalTokens(dataNode.path("total_tokens").asInt(0));
            workflowResponse.setTotalSteps(dataNode.path("total_steps").asInt(0));
            workflowResponse.setCreatedAt(dataNode.path("created_at").asLong(0));
            workflowResponse.setFinishedAt(dataNode.path("finished_at").asLong(0));
            
            return workflowResponse;
        }
    }
    
    /**
     * 执行工作流（流式模式）
     * 返回 SSE 流式响应
     */
    public Response runWorkflowStreaming(String user, Map<String, Object> inputs) throws IOException {
        String url = difyConfig.getBaseUrl() + "/workflows/run";
        if (difyConfig.getWorkflowId() != null && !difyConfig.getWorkflowId().isEmpty()) {
            url = difyConfig.getBaseUrl() + "/workflows/" + difyConfig.getWorkflowId() + "/run";
        }
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("inputs", inputs != null ? inputs : new HashMap<>());
        requestBody.put("response_mode", "streaming");
        requestBody.put("user", user);
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8")))
                .addHeader("Authorization", "Bearer " + difyConfig.getApiKey())
                .addHeader("Content-Type", "application/json")
                .build();
        
        return httpClient.newCall(request).execute();
    }
    
    /**
     * 获取工作流执行情况
     */
    public WorkflowRunStatus getWorkflowRunStatus(String workflowRunId) throws IOException {
        String url = difyConfig.getBaseUrl() + "/workflows/run/" + workflowRunId;
        
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer " + difyConfig.getApiKey())
                .addHeader("Content-Type", "application/json")
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Dify API request failed: " + response.code() + " - " + errorBody);
            }
            
            String responseBody = response.body() != null ? response.body().string() : "{}";
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            
            WorkflowRunStatus status = new WorkflowRunStatus();
            status.setId(jsonNode.path("id").asText());
            status.setWorkflowId(jsonNode.path("workflow_id").asText());
            status.setStatus(jsonNode.path("status").asText());
            status.setInputs(jsonNode.path("inputs"));
            status.setOutputs(jsonNode.path("outputs"));
            status.setError(jsonNode.path("error").asText(null));
            status.setTotalSteps(jsonNode.path("total_steps").asInt(0));
            status.setTotalTokens(jsonNode.path("total_tokens").asInt(0));
            status.setCreatedAt(jsonNode.path("created_at").asLong(0));
            status.setFinishedAt(jsonNode.path("finished_at").asLong(0));
            status.setElapsedTime(jsonNode.path("elapsed_time").asDouble(0));
            
            return status;
        }
    }
    
    /**
     * 停止工作流执行
     */
    public boolean stopWorkflow(String taskId, String user) throws IOException {
        String url = difyConfig.getBaseUrl() + "/workflows/tasks/" + taskId + "/stop";
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("user", user);
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8")))
                .addHeader("Authorization", "Bearer " + difyConfig.getApiKey())
                .addHeader("Content-Type", "application/json")
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return false;
            }
            String responseBody = response.body() != null ? response.body().string() : "{}";
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            return "success".equals(jsonNode.path("result").asText());
        }
    }
    
    /**
     * 工作流响应
     */
    public static class WorkflowResponse {
        private String workflowRunId;
        private String taskId;
        private String status;
        private JsonNode outputs;
        private String error;
        private double elapsedTime;
        private int totalTokens;
        private int totalSteps;
        private long createdAt;
        private long finishedAt;
        
        // Getters and Setters
        public String getWorkflowRunId() { return workflowRunId; }
        public void setWorkflowRunId(String workflowRunId) { this.workflowRunId = workflowRunId; }
        
        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public JsonNode getOutputs() { return outputs; }
        public void setOutputs(JsonNode outputs) { this.outputs = outputs; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        
        public double getElapsedTime() { return elapsedTime; }
        public void setElapsedTime(double elapsedTime) { this.elapsedTime = elapsedTime; }
        
        public int getTotalTokens() { return totalTokens; }
        public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
        
        public int getTotalSteps() { return totalSteps; }
        public void setTotalSteps(int totalSteps) { this.totalSteps = totalSteps; }
        
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        
        public long getFinishedAt() { return finishedAt; }
        public void setFinishedAt(long finishedAt) { this.finishedAt = finishedAt; }
    }
    
    /**
     * 工作流运行状态
     */
    public static class WorkflowRunStatus {
        private String id;
        private String workflowId;
        private String status;
        private JsonNode inputs;
        private JsonNode outputs;
        private String error;
        private int totalSteps;
        private int totalTokens;
        private long createdAt;
        private long finishedAt;
        private double elapsedTime;
        
        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getWorkflowId() { return workflowId; }
        public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public JsonNode getInputs() { return inputs; }
        public void setInputs(JsonNode inputs) { this.inputs = inputs; }
        
        public JsonNode getOutputs() { return outputs; }
        public void setOutputs(JsonNode outputs) { this.outputs = outputs; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        
        public int getTotalSteps() { return totalSteps; }
        public void setTotalSteps(int totalSteps) { this.totalSteps = totalSteps; }
        
        public int getTotalTokens() { return totalTokens; }
        public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
        
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        
        public long getFinishedAt() { return finishedAt; }
        public void setFinishedAt(long finishedAt) { this.finishedAt = finishedAt; }
        
        public double getElapsedTime() { return elapsedTime; }
        public void setElapsedTime(double elapsedTime) { this.elapsedTime = elapsedTime; }
    }
}


