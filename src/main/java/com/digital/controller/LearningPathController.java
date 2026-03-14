package com.digital.controller;

import com.digital.common.BaseResponse;
import com.digital.common.ErrorCode;
import com.digital.common.ResultUtils;
import com.digital.exception.BusinessException;
import com.digital.model.dto.learningpath.LearningPathJson;
import com.digital.model.dto.learningpath.LearningPathFlashcardMatchRequest;
import com.digital.model.dto.learningpath.LearningPathPlanRequest;
import com.digital.model.dto.learningpath.LearningPathRecommendRequest;
import com.digital.model.dto.learningpath.LearningPathRenameTopicRequest;
import com.digital.model.dto.learningpath.LearningPathSaveRequest;
import com.digital.model.entity.LearningPath;
import com.digital.model.entity.User;
import com.digital.model.vo.LearningPathFlashcardMatchVO;
import com.digital.model.vo.LearningPathGraphVO;
import com.digital.model.vo.LearningPathRecommendVO;
import com.digital.service.LearningPathChatPersistenceService;
import com.digital.service.LearningPathFlashcardMatchService;
import com.digital.service.LearningPathLightingService;
import com.digital.service.LearningPathService;
import com.digital.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 孪孪伴学 - 学习路径控制器
 * 提供规划（流式）、保存、CRUD 接口
 *
 * @author Shane
 */
@RestController
@RequestMapping("/learning-path")
public class LearningPathController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LearningPathController.class);

    @Resource
    private LearningPathService learningPathService;

    @Resource
    private LearningPathFlashcardMatchService learningPathFlashcardMatchService;

    @Resource
    private UserService userService;

    @Resource
    private LearningPathChatPersistenceService learningPathChatPersistenceService;

    @Resource
    private LearningPathLightingService learningPathLightingService;

    /**
     * 流式规划学习路径
     * 入参：用户提示词、当前学习路径（可为空）
     * 输出：SSE 流式推送 JSON 内容
     */
    @PostMapping("/plan/stream")
    public SseEmitter planStream(@RequestBody LearningPathPlanRequest request,
                                 HttpServletRequest httpRequest) {
        Long userId = resolveUserId(request.getUserId(), httpRequest);
        request.setUserId(userId);

        SseEmitter emitter = new SseEmitter(300000L);

        String sessionId = learningPathChatPersistenceService.getOrCreateSession(
                userId,
                request.getSessionId(),
                LearningPathChatPersistenceService.defaultTitleFromPrompt(request.getUserPrompt())
        );
        request.setSessionId(sessionId);

        // 先保存用户消息（学习路径规划/修改提示）
        String userContent = request.getUserPrompt();
        if (StringUtils.isNotBlank(request.getCurrentPathJson())) {
            userContent += "\n\n当前学习路径（请在此基础上修改）：\n" + request.getCurrentPathJson();
        }
        learningPathChatPersistenceService.saveUserMessage(sessionId, userId, userContent);

        StringBuilder assistantBuffer = new StringBuilder();
        AtomicBoolean assistantSaved = new AtomicBoolean(false);
        AtomicBoolean stopped = new AtomicBoolean(false);

        try {
            emitter.send(SseEmitter.event().name("meta").data(java.util.Map.of("sessionId", sessionId)));
        } catch (IOException ignored) {
        }

        Runnable persistOnce = () -> {
            if (assistantSaved.compareAndSet(false, true)) {
                String content = assistantBuffer.toString();
                if (StringUtils.isNotBlank(content)) {
                    learningPathChatPersistenceService.saveAssistantMessage(sessionId, userId, content);
                }
                learningPathChatPersistenceService.touchSession(sessionId);
            }
        };

        learningPathService.planLearningPathStream(request, () -> stopped.get(), (delta, finished) -> {
            try {
                if (!delta.isEmpty()) {
                    assistantBuffer.append(delta);
                    emitter.send(SseEmitter.event()
                            .data(delta)
                            .name("message"));
                }
                if (finished) {
                    emitter.send(SseEmitter.event()
                            .data("[DONE]")
                            .name("done"));
                    persistOnce.run();
                    emitter.complete();
                }
            } catch (IOException e) {
                log.error("发送流式数据失败", e);
                persistOnce.run();
                emitter.completeWithError(e);
            }
        });

        emitter.onError(throwable -> {
            stopped.set(true);
            persistOnce.run();
            log.error("SseEmitter 错误", throwable);
        });
        emitter.onCompletion(() -> {
            stopped.set(true);
            persistOnce.run();
        });
        emitter.onTimeout(() -> {
            log.warn("SseEmitter 超时");
            stopped.set(true);
            persistOnce.run();
            emitter.complete();
        });

        return emitter;
    }

    /**
     * 基于 WebFlux 的流式规划接口
     * 返回 Flux<ServerSentEvent<String>>，由浏览器原生 EventSource 或任意支持 SSE 的客户端消费
     *
     * 事件：
     * - message: JSON 增量片段（可能是半截 JSON）
     * - done: [DONE]
     */
    @PostMapping(value = "/plan/stream/flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> planStreamFlux(@RequestBody LearningPathPlanRequest request,
                                                        HttpServletRequest httpRequest) {
        Long userId = resolveUserId(request.getUserId(), httpRequest);
        request.setUserId(userId);

        String sessionId = learningPathChatPersistenceService.getOrCreateSession(
                userId,
                request.getSessionId(),
                LearningPathChatPersistenceService.defaultTitleFromPrompt(request.getUserPrompt())
        );
        request.setSessionId(sessionId);

        String userContent = request.getUserPrompt();
        if (StringUtils.isNotBlank(request.getCurrentPathJson())) {
            userContent += "\n\n当前学习路径（请在此基础上修改）：\n" + request.getCurrentPathJson();
        }
        learningPathChatPersistenceService.saveUserMessage(sessionId, userId, userContent);

        StringBuilder assistantBuffer = new StringBuilder();
        AtomicBoolean assistantSaved = new AtomicBoolean(false);

        Runnable persistOnce = () -> {
            if (assistantSaved.compareAndSet(false, true)) {
                String content = assistantBuffer.toString();
                if (StringUtils.isNotBlank(content)) {
                    learningPathChatPersistenceService.saveAssistantMessage(sessionId, userId, content);
                }
                learningPathChatPersistenceService.touchSession(sessionId);
            }
        };

        return Flux.<ServerSentEvent<String>>create(sink -> {
            Schedulers.boundedElastic().schedule(() -> {
                try {
                    sink.next(ServerSentEvent.builder("{\"sessionId\":\"" + sessionId + "\"}").event("meta").build());
                    learningPathService.planLearningPathStream(request, sink::isCancelled, (delta, finished) -> {
                        if (sink.isCancelled()) {
                            return;
                        }
                        if (StringUtils.isNotEmpty(delta)) {
                            assistantBuffer.append(delta);
                            sink.next(ServerSentEvent.builder(delta).event("message").build());
                        }
                        if (finished) {
                            sink.next(ServerSentEvent.builder("[DONE]").event("done").build());
                            persistOnce.run();
                            sink.complete();
                        }
                    });
                } catch (Exception e) {
                    log.error("学习路径流式规划失败", e);
                    persistOnce.run();
                    sink.error(e);
                }
            });
        }).doFinally(signalType -> persistOnce.run())
          .publishOn(Schedulers.boundedElastic());
    }

    /**
     * 保存学习路径
     * 用户确认后调用，保存到 MySQL + Neo4j
     */
    @PostMapping("/save")
    public BaseResponse<LearningPath> save(@RequestBody LearningPathSaveRequest request,
                                          HttpServletRequest httpRequest) {
        Long userId = resolveUserId(request.getUserId(), httpRequest);
        request.setUserId(userId);

        LearningPath saved = learningPathService.saveLearningPath(request);
        return ResultUtils.success(saved);
    }

    /**
     * 获取用户的学习路径列表
     */
    @GetMapping("/list")
    public BaseResponse<List<LearningPath>> list(@RequestParam(required = false) Long userId,
                                                 HttpServletRequest httpRequest) {
        Long resolvedUserId = resolveUserId(userId, httpRequest);
        List<LearningPath> list = learningPathService.listByUserId(resolvedUserId);
        return ResultUtils.success(list);
    }

    /**
     * 根据 ID 获取学习路径
     */
    @GetMapping("/{pathId}")
    public BaseResponse<LearningPath> getById(@PathVariable String pathId,
                                              @RequestParam(required = false) Long userId,
                                              HttpServletRequest httpRequest) {
        Long resolvedUserId = resolveUserId(userId, httpRequest);
        LearningPath path = learningPathService.getById(resolvedUserId, pathId);
        if (path == null) {
            return new BaseResponse<>(ErrorCode.NOT_FOUND_ERROR.getCode(), null, "学习路径不存在");
        }
        return ResultUtils.success(path);
    }

    /**
     * 获取学习路径图谱（节点 + 关系），供前端展示 Neo4j 图
     */
    @GetMapping("/{pathId}/graph")
    public BaseResponse<LearningPathGraphVO> getPathGraph(@PathVariable String pathId,
                                                          @RequestParam(required = false) Long userId,
                                                          HttpServletRequest httpRequest) {
        Long resolvedUserId = resolveUserId(userId, httpRequest);
        LearningPathGraphVO graph = learningPathService.getLearningPathGraph(resolvedUserId, pathId);
        if (graph == null) {
            return new BaseResponse<>(ErrorCode.NOT_FOUND_ERROR.getCode(), null, "学习路径不存在");
        }
        return ResultUtils.success(graph);
    }

    /**
     * 点击学习路径节点 → 匹配闪卡图谱（Neo4j Fulltext + threshold/TopK）
     * 返回命中 flashcardIds 供前端点亮，未命中置灰
     */
    @PostMapping("/{pathId}/flashcard/match")
    public BaseResponse<LearningPathFlashcardMatchVO> matchFlashcards(@PathVariable String pathId,
                                                                      @RequestParam(required = false) Long userId,
                                                                      @RequestBody LearningPathFlashcardMatchRequest request,
                                                                      HttpServletRequest httpRequest) {
        Long resolvedUserId = resolveUserId(userId, httpRequest);
        LearningPathFlashcardMatchVO vo = learningPathFlashcardMatchService.matchFlashcards(resolvedUserId, pathId, request);
        if (vo == null) {
            return new BaseResponse<>(ErrorCode.NOT_FOUND_ERROR.getCode(), null, "学习路径不存在");
        }
        // match 结果落库并触发点亮回溯（只要前端以该接口返回作为关联依据，就需要长期保存）
        try {
            learningPathService.persistFlashcardMatches(resolvedUserId, pathId, vo);
            learningPathLightingService.recomputePath(resolvedUserId, pathId);
        } catch (Exception e) {
            log.error("持久化 match 结果或回溯点亮失败: userId={}, pathId={}, error={}",
                    resolvedUserId, pathId, e.getMessage(), e);
        }
        return ResultUtils.success(vo);
    }

    /**
     * 获取学习路径 JSON（供前端渲染、AI 润色复用）
     */
    @GetMapping("/{pathId}/json")
    public BaseResponse<LearningPathJson> getPathJson(@PathVariable String pathId,
                                                      @RequestParam(required = false) Long userId,
                                                      HttpServletRequest httpRequest) {
        Long resolvedUserId = resolveUserId(userId, httpRequest);
        LearningPathJson json = learningPathService.getPathJson(resolvedUserId, pathId);
        if (json == null) {
            return new BaseResponse<>(ErrorCode.NOT_FOUND_ERROR.getCode(), null, "学习路径不存在");
        }
        return ResultUtils.success(json);
    }

    /**
     * 更新学习路径 JSON
     */
    @PutMapping("/{pathId}/json")
    public BaseResponse<Boolean> updatePathJson(@PathVariable String pathId,
                                                  @RequestParam(required = false) Long userId,
                                                  @RequestBody LearningPathJson pathJson,
                                                  HttpServletRequest httpRequest) {
        Long resolvedUserId = resolveUserId(userId, httpRequest);
        boolean ok = learningPathService.updatePathJson(resolvedUserId, pathId, pathJson);
        return ResultUtils.success(ok);
    }

    /**
     * 根据知识主题生成「建议向 AI 提问」的推荐学习知识点列表（求知姿态，供用户拿去问别的 AI）
     * 仿照豆包聊天接口，调用 Doubao 生成结构化 JSON。
     */
    @PostMapping("/recommend-questions")
    public BaseResponse<LearningPathRecommendVO> recommendKnowledgeQuestions(@RequestBody LearningPathRecommendRequest request,
                                                                             @RequestParam(required = false) Long userId,
                                                                             HttpServletRequest httpRequest) {
        Long resolvedUserId = resolveUserId(userId, httpRequest);
        if (request == null || StringUtils.isBlank(request.getTopic())) {
            return new BaseResponse<>(ErrorCode.PARAMS_ERROR.getCode(), null, "知识主题不能为空");
        }
        LearningPathRecommendVO vo = learningPathService.recommendKnowledgeQuestions(resolvedUserId, request.getTopic().trim());
        return ResultUtils.success(vo);
    }

    /**
     * 删除学习路径
     */
    @DeleteMapping("/{pathId}")
    public BaseResponse<Boolean> delete(@PathVariable String pathId,
                                        @RequestParam(required = false) Long userId,
                                        HttpServletRequest httpRequest) {
        Long resolvedUserId = resolveUserId(userId, httpRequest);
        boolean ok = learningPathService.deleteLearningPath(resolvedUserId, pathId);
        return ResultUtils.success(ok);
    }

    /**
     * 重命名学习路径的 topic
     */
    @PutMapping("/{pathId}/topic")
    public BaseResponse<Boolean> renameTopic(@PathVariable String pathId,
                                             @RequestParam(required = false) Long userId,
                                             @RequestBody LearningPathRenameTopicRequest request,
                                             HttpServletRequest httpRequest) {
        Long resolvedUserId = resolveUserId(userId, httpRequest);
        if (request == null || StringUtils.isBlank(request.getTopic())) {
            return new BaseResponse<>(ErrorCode.PARAMS_ERROR.getCode(), null, "新主题不能为空");
        }
        boolean ok = learningPathService.renameTopic(resolvedUserId, pathId, request.getTopic().trim());
        return ResultUtils.success(ok);
    }

    private Long resolveUserId(Long requestUserId, HttpServletRequest request) {
        try {
            User loginUser = userService.getLoginUser(request);
            if (loginUser != null) {
                return loginUser.getId();
            }
        } catch (Exception ignored) {
        }
        if (requestUserId != null) {
            return requestUserId;
        }
        throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
    }
}
