package com.digital.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class CareerPlanState extends AgentState {

    // 输入
    private Long userId;
    private String userInput;
    private Map<String, Object> assessmentResult; // 职业测评结果

    // 数据层
    private Map<String, Object> userProfile;
    private List<Map<String, Object>> growthRecords;

    // 检索层
    private List<Map<String, Object>> jobTrends;
    private List<Map<String, Object>> competitions;

    // 分析层
    private Map<String, Object> learningPlan; // 学习计划

    // 产出
    private Map<String, Object> plan;
    private String reportMarkdown;
    private List<String> links;

    // 运行态
    private String runId;
    private String status; // running/success/failed
    private String error;
    private String currentNode; // 当前执行的节点
    private Map<String, String> nodeStatus; // 节点状态映射: nodeName -> status (pending/running/completed/failed)

    public static String nowString() {
        return new Date().toString();
    }

    public CareerPlanState() {
        super(new HashMap<>());
    }
}