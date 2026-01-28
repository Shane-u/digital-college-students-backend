package com.digital.example;

import com.alibaba.fastjson.JSON;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;


import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;


@Slf4j
public class LangGraphTest {

    @Test
    public void test1()throws GraphStateException {
        StateGraph<AgentState> workflow = new StateGraph<>(AgentState::new)
                .addNode("step1", node_async(state -> {
                    log.info("step1:{}", state);
                    return Map.of("step1", "洗");
                }))
                .addNode("step2", node_async(state -> {
                    log.info("step2:{}", state);
                    return Map.of("step2", "切");
                }))
                .addNode("step3", node_async(state -> {
                    log.info("step3:{}", state);
                    return Map.of("step3", "炒");
                }))
                .addEdge(START, "step1")
                .addEdge("step1", "step2")
                .addEdge("step2", "step3")
                .addEdge("step3", END);

        CompiledGraph<AgentState> app = workflow.compile();
        GraphRepresentation graph = app.getGraph(GraphRepresentation.Type.PLANTUML, "炒西红柿智能体");
        System.out.println("炒西红柿智能体 PlantUML Graph:\n"+ graph.content());

        Optional<AgentState> result = app.invoke(Map.of("input", "西红柿"));

        System.out.println(JSON.toJSONString(result.get().data()));
    }

    @Test
    public void test2()throws GraphStateException {
        StateGraph<AgentState> workflow = new StateGraph<>(AgentState::new)
                .addNode("step1", node_async(state -> {
                    log.info("step1:{}", state);
                    return Map.of("step1", "开发需求来了");
                }))
                .addNode("step2", node_async(state -> {
                    log.info("step2:{}", state);
                    return Map.of("step2", "后端开发");
                }))
                .addNode("step3", node_async(state -> {
                    log.info("step3:{}", state);
                    return Map.of("step3", "前端开发");
                }))
                .addNode("step4", node_async(state -> {
                    log.info("step4:{}", state);
                    return Map.of("step4", "前后端联调");
                }))
                .addEdge(START, "step1")
                .addEdge("step1", "step2")
                .addEdge("step1", "step3")
                .addEdge("step2", "step4")
                .addEdge("step3", "step4")
                .addEdge("step4", END);
        CompiledGraph<AgentState> app = workflow.compile();
        GraphRepresentation graph= app.getGraph(GraphRepresentation.Type.PLANTUML, "项目开发智能体");
        System.out.println("项目开发智能体 PlantUML Graph:\n"+ graph.content());

        Optional<AgentState> result = app.invoke(Map.of("input", "项目A"));

        System.out.println(JSON.toJSONString(result.get().data()));
    }


    @Test
    public void test3()throws GraphStateException {
        StateGraph<AgentState> workflow = new StateGraph<>(AgentState::new)
                .addNode("step1", node_async(state -> {
                    log.info("step1:{}", state);
                    String input= (String) state.value("input").orElse("");
                    String systemInput = "你是一个舆情识别专家，你的任务是判断用户输入是正面情绪还是负面情绪，输出“正面”或者“负面”，不要输出其他解析内容。";
                    String response = doubaoChat(systemInput,input);
                    return Map.of("type", response);
                }))
                .addNode("step2", node_async(state -> {
                    log.info("step2:{}", state);
                    String input= (String) state.value("input").orElse("");
                    String chatResponse= doubaoChat("","你是一个舆情处理专家，你的任务是将用户的问题派发到不同的部门，" +
                            "如：医院、学校、银行。仅输出最终的结果，不需要解析内容。\n用户输入："+input);
                    return Map.of("classify", chatResponse);
                }))
                .addEdge(START, "step1")
                .addConditionalEdges("step1",
                        AsyncEdgeAction.edge_async(this::getType),
                        Map.of(
                                "step2", "step2",
                                "end",END
                        )
                )
                .addEdge("step2", END);

        CompiledGraph<AgentState> app = workflow.compile();
        GraphRepresentation graph= app.getGraph(GraphRepresentation.Type.PLANTUML, "舆情识别智能体");
        System.out.println("舆情识别智能体 PlantUML Graph:\n"+ graph.content());

        Optional<AgentState> result = app.invoke(Map.of("input", "我的银行卡账号被冻结了呜呜呜"));

        System.out.println(JSON.toJSONString(result.get().data()));
    }

    private String getType(AgentState agentState) {
        String type= (String) agentState.value("type").orElse("");
        if(type.contains("负面")){
            return "step2";
        }else{
            return "end";
        }
    }


    public static String doubaoChat(String systemInput, String input) {
        // 从环境变量中获取API密钥
        String apiKey = System.getenv("ARK_API_KEY");
        //  .
        ArkService arkService = ArkService.builder().apiKey(apiKey).baseUrl("https://ark.cn-beijing.volces.com/api/v3").build();

        // 初始化消息列表
        List<ChatMessage> chatMessages = new ArrayList<>();

        // 创建用户消息
        ChatMessage userMessage = ChatMessage.builder()
                .role(ChatMessageRole.USER) // 设置消息角色为用户
                .content(input) // 设置消息内容
                .build();

        ChatMessage systemMessage = ChatMessage.builder()
                .role(ChatMessageRole.SYSTEM) // 设置消息角色为系统
                .content(systemInput) // 设置消息内容
                .build();

        // 将用户消息添加到消息列表
        chatMessages.add(userMessage);

        // 将系统消息添加到消息列表
        chatMessages.add(systemMessage);

        // 创建聊天完成请求
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model("doubao-1-5-lite-32k-250115")// Get Model ID: https://www.volcengine.com/docs/82379/1330310 .
                .messages(chatMessages) // 设置消息列表
                .build();

        // 发送聊天完成请求并打印响应
        String response = "";
        try {
            // 获取响应并打印每个选择的消息内容
            response = arkService.createChatCompletion(chatCompletionRequest)
                    .getChoices()
                    .stream()
                    .map(choice -> String.valueOf(choice.getMessage().getContent()))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        } finally {
            // 关闭服务执行器
            arkService.shutdownExecutor();
        }
        return response;
    }

    /**
     * AI猜谜游戏测试
     * 核心玩法：AI出题→玩家答题→判断答案→难度升级→计分→游戏结束
     */
    public static void main(String[] args) throws GraphStateException {
        StateGraph<AgentState> workflow = new StateGraph<>(AgentState::new)
                // 初始化游戏状态
                .addNode("init", node_async(state -> {
                    log.info("初始化游戏状态");
                    String topicType = (String) state.value("topicType").orElse("动物谜语");
                    return Map.of(
                            "score", 0,
                            "difficulty", "简单",
                            "wrongCount", 0,
                            "correctCount", 0,
                            "topicType", topicType,
                            "gameStatus", "playing"
                    );
                }))
                // AI出题节点
                .addNode("generateQuestion", node_async(state -> {
                    log.info("生成题目");
                    String difficulty = (String) state.value("difficulty").orElse("简单");
                    String topicType = (String) state.value("topicType").orElse("动物谜语");
                    
                    String prompt = String.format(
                            "你是一个出题专家，请出一道%s类型的%s难度的谜语或脑筋急转弯。\n" +
                            "要求：\n" +
                            "1. 题目要有趣、有挑战性\n" +
                            "2. 只输出JSON格式：{\"question\": \"题目内容\", \"answer\": \"标准答案\", \"hint\": \"提示信息\"}\n" +
                            "3. 不要输出其他内容，只输出JSON",
                            topicType, difficulty
                    );
                    
                    String response = doubaoChat("", prompt);
                    log.info("AI出题响应: {}", response);
                    
                    // 解析JSON响应（简化处理，实际应该用JSON解析器）
                    String question = extractJsonValue(response, "question");
                    String answer = extractJsonValue(response, "answer");
                    String hint = extractJsonValue(response, "hint");
                    
                    return Map.of(
                            "currentQuestion", question,
                            "currentAnswer", answer,
                            "currentHint", hint,
                            "questionNumber", ((Integer) state.value("correctCount").orElse(0)) + 1
                    );
                }))
                // 判断答案节点
                .addNode("judgeAnswer", node_async(state -> {
                    log.info("判断答案");
                    String userAnswer = (String) state.value("userAnswer").orElse("");
                    String correctAnswer = (String) state.value("currentAnswer").orElse("");
                    String currentQuestion = (String) state.value("currentQuestion").orElse("");
                    
                    // 如果没有用户答案，抛出异常或返回特殊状态（实际应该由外部控制）
                    if (StringUtils.isBlank(userAnswer)) {
                        log.warn("没有用户答案，这不应该发生");
                        // 返回一个特殊状态，让路由函数知道需要等待
                        return Map.of("needUserInput", true);
                    }
                    
                    // 如果用户输入"退出"，直接返回游戏结束
                    if ("退出".equals(userAnswer.trim()) || "exit".equalsIgnoreCase(userAnswer.trim())) {
                        return Map.of("isCorrect", false, "shouldExit", true);
                    }
                    
                    // 使用AI判断答案是否正确（支持谐音、同义替换）
                    String judgePrompt = String.format(
                            "你是一个答案判断专家。请判断玩家的答案是否正确。\n" +
                            "题目：%s\n" +
                            "标准答案：%s\n" +
                            "玩家答案：%s\n\n" +
                            "要求：\n" +
                            "1. 如果玩家答案正确（包括谐音、同义替换、意思相近），输出：{\"isCorrect\": true, \"reason\": \"判断理由\"}\n" +
                            "2. 如果玩家答案错误，输出：{\"isCorrect\": false, \"reason\": \"判断理由\"}\n" +
                            "3. 只输出JSON格式，不要输出其他内容",
                            currentQuestion, correctAnswer, userAnswer
                    );
                    
                    String judgeResponse = doubaoChat("", judgePrompt);
                    log.info("AI判断响应: {}", judgeResponse);
                    
                    boolean isCorrect = extractJsonBoolean(judgeResponse, "isCorrect");
                    String reason = extractJsonValue(judgeResponse, "reason");
                    
                    return Map.of("isCorrect", isCorrect, "judgeReason", reason);
                }))
                // 答对处理节点
                .addNode("handleCorrect", node_async(state -> {
                    log.info("答对处理");
                    int score = (Integer) state.value("score").orElse(0);
                    int correctCount = (Integer) state.value("correctCount").orElse(0);
                    String difficulty = (String) state.value("difficulty").orElse("简单");
                    
                    // 分数+1，答对次数+1
                    score += 1;
                    correctCount += 1;
                    
                    // 难度升级逻辑
                    String newDifficulty = difficulty;
                    if (correctCount >= 7) {
                        newDifficulty = "困难";
                    } else if (correctCount >= 3) {
                        newDifficulty = "中等";
                    }
                    
                    // 生成鼓励语（简化，避免长时间等待）
                    String encouragement = "太棒了！继续加油！";
                    if (correctCount % 3 == 0) {
                        // 每3题生成一次AI鼓励语
                        encouragement = doubaoChat("", 
                                "玩家答对了题目，请生成一句简短有趣的鼓励语（不超过20字），比如'太棒了！继续加油！'");
                    }
                    
                    String message = String.format("✅ 恭喜答对！得分：%d分 | 当前难度：%s | %s", 
                            score, newDifficulty, encouragement);
                    
                    return Map.of(
                            "score", score,
                            "correctCount", correctCount,
                            "difficulty", newDifficulty,
                            "wrongCount", 0, // 重置错误次数
                            "encouragement", encouragement,
                            "message", message
                    );
                }))
                // 答错处理节点
                .addNode("handleWrong", node_async(state -> {
                    log.info("答错处理");
                    int wrongCount = (Integer) state.value("wrongCount").orElse(0);
                    wrongCount += 1;
                    
                    String currentHint = (String) state.value("currentHint").orElse("");
                    String currentAnswer = (String) state.value("currentAnswer").orElse("");
                    
                    String message;
                    if (wrongCount >= 3) {
                        // 答错3次，提示答案
                        message = String.format("❌ 很遗憾答错了！正确答案是：%s\n💡 提示：%s", currentAnswer, currentHint);
                    } else {
                        // 简化提示，避免长时间等待AI生成
                        if (StringUtils.isNotBlank(currentHint)) {
                            message = String.format("❌ 答错了！再试试～\n💡 提示：%s（还有%d次机会）", currentHint, 3 - wrongCount);
                        } else {
                            message = String.format("❌ 答错了！再想想～（还有%d次机会）", 3 - wrongCount);
                        }
                    }
                    
                    return Map.of(
                            "wrongCount", wrongCount,
                            "message", message,
                            "shouldShowAnswer", wrongCount >= 3
                    );
                }))
                // 游戏结束节点
                .addNode("gameOver", node_async(state -> {
                    log.info("游戏结束");
                    int score = (Integer) state.value("score").orElse(0);
                    int correctCount = (Integer) state.value("correctCount").orElse(0);
                    
                    // 根据分数计算段位
                    String rank = calculateRank(score);
                    
                    // 生成结束语
                    String endMessage = doubaoChat("", 
                            String.format("游戏结束！玩家答对了%d题，得分%d分，段位是%s。请生成一句有趣的结束语（不超过30字）", 
                                    correctCount, score, rank));
                    
                    return Map.of(
                            "gameStatus", "ended",
                            "finalScore", score,
                            "finalRank", rank,
                            "endMessage", endMessage,
                            "summary", String.format("游戏结束！\n总得分：%d分\n答对题数：%d题\n段位：%s\n%s", 
                                    score, correctCount, rank, endMessage)
                    );
                }))
                // 构建边
                .addEdge(START, "init")
                .addEdge("init", "generateQuestion")
                // 生成题目后，根据是否有userAnswer决定下一步
                .addConditionalEdges("generateQuestion",
                        AsyncEdgeAction.edge_async(LangGraphTest::routeAfterGenerate),
                        Map.of(
                                "judge", "judgeAnswer",  // 如果有userAnswer，进入判断
                                "wait", END  // 如果没有userAnswer，结束等待外部输入
                        )
                )
                .addConditionalEdges("judgeAnswer",
                        AsyncEdgeAction.edge_async(LangGraphTest::routeAfterJudge),
                        Map.of(
                                "correct", "handleCorrect",
                                "wrong", "handleWrong",
                                "exit", "gameOver"
                        )
                )
                .addConditionalEdges("handleCorrect",
                        AsyncEdgeAction.edge_async(LangGraphTest::routeAfterCorrect),
                        Map.of(
                                "continue", "generateQuestion",
                                "end", "gameOver"
                        )
                )
                .addConditionalEdges("handleWrong",
                        AsyncEdgeAction.edge_async(LangGraphTest::routeAfterWrong),
                        Map.of(
                                "retry", "judgeAnswer",
                                "next", "generateQuestion",
                                "end", "gameOver"
                        )
                )
                .addEdge("gameOver", END);

        CompiledGraph<AgentState> app = workflow.compile();
        GraphRepresentation graph = app.getGraph(GraphRepresentation.Type.PLANTUML, "AI猜谜游戏");
        System.out.println("AI猜谜游戏 PlantUML Graph:\n" + graph.content());

        // 模拟游戏流程
        System.out.println("========== AI猜谜游戏开始 ==========");
        
        // 初始化：设置题库类型
        Map<String, Object> initialData = new HashMap<>();
        initialData.put("topicType", "动物谜语");
        Optional<AgentState> state = app.invoke(initialData);
        
        // 模拟多轮游戏（实际应该是用户交互循环）
        int round = 1;
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        
        while (round <= 10 && !"ended".equals(state.get().value("gameStatus").orElse(""))) {
            System.out.println("\n========== 【第" + round + "题】 ==========");
            
            // 第一步：生成题目（如果还没有题目）
            if (StringUtils.isBlank((String) state.get().value("currentQuestion").orElse(""))) {
                // 调用状态机：初始化 → 生成题目 → 进入judgeAnswer（但没有userAnswer，会返回needUserInput）
                // 由于judgeAnswer没有userAnswer，状态机会暂停或返回特殊状态
                try {
                    state = app.invoke(state.get().data());
                } catch (Exception e) {
                    log.error("生成题目失败", e);
                    break;
                }
            }
            
            String question = (String) state.get().value("currentQuestion").orElse("");
            if (StringUtils.isBlank(question)) {
                System.out.println("⚠️ 无法生成题目，游戏结束");
                break;
            }
            
            System.out.println("📝 题目：" + question);
            System.out.print("💭 请输入你的答案（输入'退出'结束游戏）：");
            
            // 第二步：从控制台读取用户输入（这里会阻塞等待用户输入）
            String userAnswer = scanner.nextLine().trim();
            
            if (StringUtils.isBlank(userAnswer)) {
                System.out.println("⚠️ 答案不能为空，请重新输入！");
                continue;
            }
            
            System.out.println("你的答案：" + userAnswer);
            
            // 第三步：设置用户答案并继续执行（判断答案 → 处理结果）
            Map<String, Object> gameData = new HashMap<>(state.get().data());
            gameData.put("userAnswer", userAnswer);
            gameData.remove("needUserInput");  // 清除等待标志
            
            try {
                state = app.invoke(gameData);
            } catch (Exception e) {
                log.error("判断答案失败", e);
                break;
            }
            
            // 立即显示判断结果（正向反馈）
            String message = (String) state.get().value("message").orElse("");
            if (StringUtils.isNotBlank(message)) {
                System.out.println("\n" + message);
            }
            
            // 显示当前分数和难度
            int score = (Integer) state.get().value("score").orElse(0);
            int correctCount = (Integer) state.get().value("correctCount").orElse(0);
            String difficulty = (String) state.get().value("difficulty").orElse("简单");
            System.out.println(String.format("📊 当前得分：%d分 | 答对题数：%d题 | 难度：%s", 
                    score, correctCount, difficulty));
            
            // 检查是否需要进入下一题
            Boolean shouldShowAnswer = (Boolean) state.get().value("shouldShowAnswer").orElse(false);
            if (shouldShowAnswer) {
                // 答错3次，显示答案后进入下一题
                System.out.println("\n按回车键继续下一题...");
                scanner.nextLine();
            }
            
            // 清除userAnswer和相关状态，准备下一题
            state.get().data().remove("userAnswer");
            state.get().data().remove("needUserInput");
            state.get().data().remove("currentQuestion");  // 清除题目，让下一轮重新生成
            state.get().data().remove("currentAnswer");
            state.get().data().remove("currentHint");
            round++;
        }
        
        scanner.close();
        
        // 游戏结束
        if ("ended".equals(state.get().value("gameStatus").orElse(""))) {
            System.out.println("\n" + state.get().value("summary").orElse(""));
        }
        
        System.out.println("\n========== 游戏数据 ==========");
        System.out.println(JSON.toJSONString(state.get().data()));
    }

    /**
     * 生成题目后的路由
     */
    private static String routeAfterGenerate(AgentState state) {
        String userAnswer = (String) state.value("userAnswer").orElse("");
        // 如果有用户答案，进入判断节点
        if (StringUtils.isNotBlank(userAnswer)) {
            return "judge";
        }
        // 如果没有用户答案，结束等待外部输入
        return "wait";
    }

    /**
     * 判断答案后的路由
     */
    private static String routeAfterJudge(AgentState state) {
        // 检查是否需要用户输入（这不应该发生，因为外部已经设置了userAnswer）
        Boolean needUserInput = (Boolean) state.value("needUserInput").orElse(false);
        if (needUserInput) {
            // 如果确实需要用户输入，返回END让流程结束（实际应该由外部控制）
            log.warn("需要用户输入，但状态机无法等待，返回END");
            return "exit";  // 返回exit让流程结束，实际应该由外部控制
        }
        
        Boolean shouldExit = (Boolean) state.value("shouldExit").orElse(false);
        if (shouldExit) {
            return "exit";
        }
        
        Boolean isCorrect = (Boolean) state.value("isCorrect").orElse(false);
        return isCorrect ? "correct" : "wrong";
    }

    /**
     * 答对后的路由
     */
    private static String routeAfterCorrect(AgentState state) {
        int correctCount = (Integer) state.value("correctCount").orElse(0);
        // 答对10题或更多，游戏结束
        if (correctCount >= 10) {
            return "end";
        }
        return "continue";
    }

    /**
     * 答错后的路由
     */
    private static String routeAfterWrong(AgentState state) {
        Boolean shouldShowAnswer = (Boolean) state.value("shouldShowAnswer").orElse(false);
        
        // 答错3次，显示答案后进入下一题
        if (shouldShowAnswer) {
            return "next";
        }
        // 答错但未到3次，可以重试
        return "retry";
    }

    /**
     * 计算段位
     */
    private static String calculateRank(int score) {
        if (score >= 10) {
            return "王者谜神";
        } else if (score >= 7) {
            return "钻石谜师";
        } else if (score >= 5) {
            return "黄金谜手";
        } else if (score >= 3) {
            return "白银谜客";
        } else {
            return "青铜谜手";
        }
    }

    /**
     * 从JSON字符串中提取值（简化版，实际应该用JSON解析器）
     */
    private static String extractJsonValue(String json, String key) {
        try {
            // 简化处理，实际应该用JSON解析器
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            log.warn("解析JSON失败: {}", e.getMessage());
        }
        return "";
    }

    /**
     * 从JSON字符串中提取布尔值
     */
    private static boolean extractJsonBoolean(String json, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*(true|false)";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return Boolean.parseBoolean(m.group(1));
            }
        } catch (Exception e) {
            log.warn("解析JSON布尔值失败: {}", e.getMessage());
        }
        return false;
    }

}
