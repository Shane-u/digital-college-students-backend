package com.digital.controller;

import com.digital.common.BaseResponse;
import com.digital.common.ErrorCode;
import com.digital.common.ResultUtils;
import com.digital.exception.BusinessException;
import com.digital.model.dto.learningpath.LearningPathJson;
import com.digital.model.dto.learningpath.LearningPathPlanRequest;
import com.digital.model.dto.learningpath.LearningPathSaveRequest;
import com.digital.model.entity.LearningPath;
import com.digital.model.entity.User;
import com.digital.service.LearningPathService;
import com.digital.service.UserService;
import lombok.extern.slf4j.Slf4j;
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

/**
 * 孪孪伴学 - 学习路径控制器
 * 提供规划（流式）、保存、CRUD 接口
 *
 * @author Shane
 */
@RestController
@RequestMapping("/learning-path")
@Slf4j
public class LearningPathController {

    @Resource
    private LearningPathService learningPathService;

    @Resource
    private UserService userService;

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

        learningPathService.planLearningPathStream(request, (delta, finished) -> {
            try {
                if (!delta.isEmpty()) {
                    emitter.send(SseEmitter.event()
                            .data(delta)
                            .name("message"));
                }
                if (finished) {
                    emitter.send(SseEmitter.event()
                            .data("[DONE]")
                            .name("done"));
                    emitter.complete();
                }
            } catch (IOException e) {
                log.error("发送流式数据失败", e);
                emitter.completeWithError(e);
            }
        });

        emitter.onError(throwable -> log.error("SseEmitter 错误", throwable));
        emitter.onTimeout(() -> {
            log.warn("SseEmitter 超时");
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

        return Flux.<ServerSentEvent<String>>create(sink -> {
            Schedulers.boundedElastic().schedule(() -> {
                try {
                    learningPathService.planLearningPathStream(request, (delta, finished) -> {
                        if (sink.isCancelled()) {
                            return;
                        }
                        if (StringUtils.isNotEmpty(delta)) {
                            sink.next(ServerSentEvent.builder(delta).event("message").build());
                        }
                        if (finished) {
                            sink.next(ServerSentEvent.builder("[DONE]").event("done").build());
                            sink.complete();
                        }
                    });
                } catch (Exception e) {
                    log.error("学习路径流式规划失败", e);
                    sink.error(e);
                }
            });
        }).publishOn(Schedulers.boundedElastic());
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
