package com.digital.service.aiinterview.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.digital.common.ErrorCode;
import com.digital.exception.ThrowUtils;
import com.digital.mapper.AiInterviewSessionMapper;
import com.digital.mapper.InterviewAnswerMapper;
import com.digital.mapper.InterviewQuestionMapper;
import com.digital.mapper.InterviewReportMapper;
import com.digital.model.entity.AiInterviewSession;
import com.digital.model.entity.CandidateResume;
import com.digital.model.entity.InterviewAnswer;
import com.digital.model.entity.InterviewQuestion;
import com.digital.model.entity.InterviewChatMessage;
import com.digital.model.entity.InterviewReport;
import com.digital.model.vo.aiinterview.AnswerVO;
import com.digital.model.vo.aiinterview.InterviewReportVO;
import com.digital.model.vo.aiinterview.InterviewSessionVO;
import com.digital.model.vo.aiinterview.QuestionVO;
import com.digital.service.aiinterview.AsrClient;
import com.digital.service.aiinterview.AiInterviewFileStorageService;
import com.digital.service.aiinterview.InterviewChatService;
import com.digital.service.aiinterview.InterviewSessionService;
import com.digital.service.aiinterview.LlmClient;
import com.digital.service.aiinterview.ResumeService;
import com.digital.service.aiinterview.TtsClient;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class InterviewSessionServiceImpl implements InterviewSessionService {

    private final AiInterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewAnswerMapper answerMapper;
    private final InterviewReportMapper reportMapper;

    private final ResumeService resumeService;
    private final LlmClient llmClient;
    private final TtsClient ttsClient;
    private final AsrClient asrClient;
    private final AiInterviewFileStorageService fileStorageService;
    private final InterviewChatService interviewChatService;

    @Override
    public InterviewSessionVO createSession(Long userId,
                                            Long resumeId,
                                            String interviewType,
                                            String language,
                                            String difficulty,
                                            String persona,
                                            Integer durationMinutes,
                                            Boolean enableCoding,
                                            Boolean enableRealtimeHints) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        ThrowUtils.throwIf(resumeId == null, ErrorCode.PARAMS_ERROR, "resumeId 不能为空");

        // 校验简历存在
        resumeService.getResumeEntity(userId, resumeId);

        AiInterviewSession session = new AiInterviewSession();
        session.setUserId(userId);
        session.setResumeId(resumeId);
        session.setType(StringUtils.defaultIfBlank(interviewType, "MIXED"));
        session.setLanguage(StringUtils.defaultIfBlank(language, "zh-CN"));
        session.setDifficulty(StringUtils.defaultIfBlank(difficulty, "MID"));
        session.setPersona(StringUtils.defaultIfBlank(persona, "mentor"));
        session.setConfigJson(buildConfigJson(durationMinutes, enableCoding, enableRealtimeHints));
        session.setStatus("CREATED");
        session.setCreateTime(new Date());
        session.setUpdateTime(new Date());
        sessionMapper.insert(session);

        InterviewSessionVO vo = new InterviewSessionVO();
        vo.setSessionId(session.getId());
        vo.setResumeId(resumeId);
        vo.setStatus(session.getStatus());
        vo.setWelcomeMessage(buildWelcome(session));
        return vo;
    }

    @Override
    public QuestionVO nextQuestion(Long userId, Long sessionId, boolean needTtsAudio) {
        AiInterviewSession session = getSession(userId, sessionId);
        if (!"RUNNING".equals(session.getStatus())) {
            session.setStatus("RUNNING");
            session.setStartedAt(session.getStartedAt() == null ? new Date() : session.getStartedAt());
            session.setUpdateTime(new Date());
            sessionMapper.updateById(session);
        }

        int nextOrderNo = getNextOrderNo(sessionId);
        String questionText = generateQuestion(session, nextOrderNo);
        InterviewQuestion q = new InterviewQuestion();
        q.setSessionId(sessionId);
        q.setOrderNo(nextOrderNo);
        q.setType(pickQuestionType(session, nextOrderNo));
        q.setContent(questionText);
        q.setCreateTime(new Date());
        q.setUpdateTime(new Date());
        questionMapper.insert(q);

        QuestionVO vo = new QuestionVO();
        vo.setQuestionId(q.getId());
        vo.setOrderNo(q.getOrderNo());
        vo.setQuestionType(q.getType());
        vo.setQuestionText(q.getContent());
        if (needTtsAudio) {
            String audioUrl = ttsClient.ttsToUrl(q.getContent(), null, null, null);
            vo.setAudioUrl(audioUrl);
        }
        return vo;
    }

    @Override
    public AnswerVO uploadAudioAnswer(Long userId,
                                     Long sessionId,
                                     Long questionId,
                                     Integer durationSeconds,
                                     MultipartFile audioFile) {
        AiInterviewSession session = getSession(userId, sessionId);
        InterviewQuestion question = getQuestion(sessionId, questionId);

        ThrowUtils.throwIf(audioFile == null || audioFile.isEmpty(), ErrorCode.PARAMS_ERROR, "音频为空");
        String audioUrl = fileStorageService.uploadAudioFile(userId, audioFile);

        AsrClient.AsrResult asr = asrClient.transcribe(toBytes(audioFile), audioFile.getContentType());
        String textAnswer = asr == null ? null : asr.getText();
        ThrowUtils.throwIf(StringUtils.isBlank(textAnswer), ErrorCode.OPERATION_ERROR,
                "ASR 识别失败：" + (asr == null ? "unknown" : StringUtils.defaultString(asr.getError())));

        InterviewAnswer answer = new InterviewAnswer();
        answer.setSessionId(sessionId);
        answer.setQuestionId(questionId);
        answer.setUserId(userId);
        answer.setAudioUrl(audioUrl);
        answer.setTextAnswer(textAnswer);
        answer.setDurationSeconds(durationSeconds);
        answer.setAsrConfidence(asr == null ? null : asr.getConfidence());
        answer.setCreateTime(new Date());
        answer.setUpdateTime(new Date());

        String evaluationJson = evaluateAnswer(session, question, textAnswer, durationSeconds);
        answer.setEvaluationJson(evaluationJson);
        answerMapper.insert(answer);

        interviewChatService.appendMessage(sessionId, userId, "user", textAnswer, questionId, answer.getId());

        AnswerVO vo = new AnswerVO();
        vo.setAnswerId(answer.getId());
        vo.setQuestionId(questionId);
        vo.setTextAnswer(textAnswer);
        vo.setAsrConfidence(answer.getAsrConfidence());
        vo.setEvaluationJson(evaluationJson);
        return vo;
    }

    @Override
    public AnswerVO uploadTextAnswer(Long userId,
                                     Long sessionId,
                                     Long questionId,
                                     Integer durationSeconds,
                                     String textAnswer,
                                     Double asrConfidence) {
        AiInterviewSession session = getSession(userId, sessionId);
        InterviewQuestion question = getQuestion(sessionId, questionId);

        ThrowUtils.throwIf(StringUtils.isBlank(textAnswer), ErrorCode.PARAMS_ERROR, "回答内容为空");

        InterviewAnswer answer = new InterviewAnswer();
        answer.setSessionId(sessionId);
        answer.setQuestionId(questionId);
        answer.setUserId(userId);
        answer.setTextAnswer(textAnswer);
        answer.setDurationSeconds(durationSeconds);
        answer.setAsrConfidence(asrConfidence);
        answer.setCreateTime(new Date());
        answer.setUpdateTime(new Date());

        String evaluationJson = evaluateAnswer(session, question, textAnswer, durationSeconds);
        answer.setEvaluationJson(evaluationJson);
        answerMapper.insert(answer);

        interviewChatService.appendMessage(sessionId, userId, "user", textAnswer, questionId, answer.getId());

        AnswerVO vo = new AnswerVO();
        vo.setAnswerId(answer.getId());
        vo.setQuestionId(questionId);
        vo.setTextAnswer(textAnswer);
        vo.setAsrConfidence(answer.getAsrConfidence());
        vo.setEvaluationJson(evaluationJson);
        return vo;
    }

    @Override
    public InterviewReportVO finish(Long userId, Long sessionId) {
        AiInterviewSession session = getSession(userId, sessionId);
        session.setStatus("FINISHED");
        session.setEndedAt(new Date());
        session.setUpdateTime(new Date());
        sessionMapper.updateById(session);

        // 结束面试时：总是基于最新“简历 + 聊天记录 + Q&A”重新生成报告（覆盖旧报告），避免缓存旧结果
        return generateOrGetReport(userId, sessionId, true);
    }

    @Override
    public InterviewReportVO getReport(Long userId, Long sessionId) {
        getSession(userId, sessionId);
        return generateOrGetReport(userId, sessionId, false);
    }

    private InterviewReportVO generateOrGetReport(Long userId, Long sessionId, boolean generateIfMissing) {
        QueryWrapper<InterviewReport> qw = new QueryWrapper<>();
        qw.eq("userId", userId).eq("sessionId", sessionId).eq("isDelete", 0);
        InterviewReport existing = reportMapper.selectOne(qw);
        // getReport：若已有报告则直接返回
        // finish：即使已有报告也要覆盖重算（generateIfMissing=true 的场景）
        if (existing != null && StringUtils.isNotBlank(existing.getReportJson()) && !generateIfMissing) {
            InterviewReportVO vo = new InterviewReportVO();
            vo.setReportId(existing.getId());
            vo.setSessionId(sessionId);
            vo.setReportJson(existing.getReportJson());
            return vo;
        }
        ThrowUtils.throwIf(existing == null && !generateIfMissing, ErrorCode.NOT_FOUND_ERROR, "报告尚未生成");

        AiInterviewSession session = getSession(userId, sessionId);
        CandidateResume resume = resumeService.getResumeEntity(userId, session.getResumeId());
        List<InterviewQuestion> questions = questionMapper.selectList(new QueryWrapper<InterviewQuestion>()
                .eq("sessionId", sessionId).eq("isDelete", 0).orderByAsc("orderNo"));
        List<InterviewAnswer> answers = answerMapper.selectList(new QueryWrapper<InterviewAnswer>()
                .eq("sessionId", sessionId).eq("isDelete", 0).orderByAsc("createTime"));
        List<InterviewChatMessage> chatMessages = interviewChatService.listBySessionId(sessionId);

        String reportJson = summarize(session, resume, questions, answers, chatMessages);

        InterviewReport report;
        if (existing != null) {
            report = existing;
            report.setReportJson(reportJson);
            report.setUpdateTime(new Date());
            reportMapper.updateById(report);
        } else {
            report = new InterviewReport();
            report.setUserId(userId);
            report.setSessionId(sessionId);
            report.setResumeId(session.getResumeId());
            report.setReportJson(reportJson);
            report.setCreateTime(new Date());
            report.setUpdateTime(new Date());
            reportMapper.insert(report);
        }

        InterviewReportVO vo = new InterviewReportVO();
        vo.setReportId(report.getId());
        vo.setSessionId(sessionId);
        vo.setReportJson(reportJson);
        return vo;
    }

    private AiInterviewSession getSession(Long userId, Long sessionId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        ThrowUtils.throwIf(sessionId == null, ErrorCode.PARAMS_ERROR, "sessionId 不能为空");
        QueryWrapper<AiInterviewSession> qw = new QueryWrapper<>();
        qw.eq("id", sessionId).eq("userId", userId).eq("isDelete", 0);
        AiInterviewSession session = sessionMapper.selectOne(qw);
        ThrowUtils.throwIf(session == null, ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        return session;
    }

    private InterviewQuestion getQuestion(Long sessionId, Long questionId) {
        ThrowUtils.throwIf(questionId == null, ErrorCode.PARAMS_ERROR, "questionId 不能为空");
        QueryWrapper<InterviewQuestion> qw = new QueryWrapper<>();
        qw.eq("id", questionId).eq("sessionId", sessionId).eq("isDelete", 0);
        InterviewQuestion q = questionMapper.selectOne(qw);
        ThrowUtils.throwIf(q == null, ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        return q;
    }

    private int getNextOrderNo(Long sessionId) {
        QueryWrapper<InterviewQuestion> qw = new QueryWrapper<>();
        qw.eq("sessionId", sessionId).eq("isDelete", 0).orderByDesc("orderNo").last("limit 1");
        InterviewQuestion last = questionMapper.selectOne(qw);
        return last == null || last.getOrderNo() == null ? 1 : last.getOrderNo() + 1;
    }

    private String pickQuestionType(AiInterviewSession session, int orderNo) {
        String type = StringUtils.defaultIfBlank(session.getType(), "MIXED").toUpperCase();
        if (!"MIXED".equals(type)) {
            return type;
        }
        // MIXED：简单轮转
        return orderNo % 2 == 0 ? "TECHNICAL" : "BEHAVIORAL";
    }

    private String generateQuestion(AiInterviewSession session, int orderNo) {
        CandidateResume resume = resumeService.getResumeEntity(session.getUserId(), session.getResumeId());
        String qType = pickQuestionType(session, orderNo);
        String system = """
                你是一名面试官，请直接输出本轮要问候选人的“具体问题文本”，而不是输出“下一道面试问题”这几个字。
                不要输出任何解释、前缀、编号或 Markdown，只保留一句纯文本问题。
                你需要根据候选人简历内容进行追问，问题要具体、有区分度。
                面试官风格由 persona 决定（mentor/strict/hr 等），语言由 language 决定。
                """;
        String user = """
                persona=%s
                language=%s
                difficulty=%s
                interviewType=%s
                questionType=%s
                orderNo=%d

                简历结构化 JSON：
                %s

                简历全文：
                %s
                """.formatted(session.getPersona(), session.getLanguage(), session.getDifficulty(), session.getType(), qType, orderNo,
                StringUtils.defaultString(resume.getParsedJson()), StringUtils.defaultString(resume.getRawText()));
        return llmClient.complete(system, user).trim();
    }

    private String evaluateAnswer(AiInterviewSession session,
                                  InterviewQuestion question,
                                  String textAnswer,
                                  Integer durationSeconds) {
        String system = """
                你是面试官评分助手。请输出严格 JSON（不要 Markdown），字段：
                contentScore(0-10), communicationScore(0-10), logicScore(0-10),
                timeSuggestion, improvementTips[]。
                不要编造不存在的事实，只根据题目与回答评估。
                """;
        String user = """
                persona=%s
                questionType=%s
                question=%s
                answer=%s
                durationSeconds=%s
                """.formatted(session.getPersona(), question.getType(), question.getContent(), textAnswer,
                durationSeconds == null ? "" : durationSeconds.toString());
        return llmClient.complete(system, user).trim();
    }

    private String summarize(AiInterviewSession session,
                             CandidateResume resume,
                             List<InterviewQuestion> questions,
                             List<InterviewAnswer> answers,
                             List<InterviewChatMessage> chatMessages) {
        String system = """
                你是面试复盘官。请输出严格 JSON（不要 Markdown）。

                重要：本报告的评判应当“以面试过程为主、简历为辅”。
                - 面试过程 = AI 的提问 + 用户的回答（包括实时语音聊天记录与逐题 Q&A）。
                - 简历仅用于提供背景，不代表真实能力，不允许因为简历漂亮就给高分；当对话表现与简历不一致时，以对话表现为准。

                输出字段建议：
                overallScore(0-100), dimensionScores{technical,communication,logic,confidence},
                highlights[], weaknesses[], questionSummaries[], suggestions[], hiringRecommendation。

                证据要求：
                - highlights/weaknesses/questionSummaries/suggestions 中尽量引用“用户回答原话片段”作为证据（用引号包起来）。
                - 如果用户回答很短、跑题、或缺少关键内容，需要明确扣分并解释原因。

                约束：
                - 只基于输入材料总结，不要编造事实。
                - 不要输出 Markdown。
                """;
        StringBuilder sb = new StringBuilder();
        sb.append("sessionType=").append(session.getType()).append("\n");
        sb.append("persona=").append(session.getPersona()).append("\n");
        sb.append("difficulty=").append(session.getDifficulty()).append("\n\n");
        sb.append("resumeJson=").append(StringUtils.defaultString(resume.getParsedJson())).append("\n\n");

        if (chatMessages != null && !chatMessages.isEmpty()) {
            sb.append("对话记录（实时语音/逐题回答的完整记录）：\n");
            for (InterviewChatMessage m : chatMessages) {
                sb.append(m.getRole()).append(": ").append(StringUtils.defaultString(m.getContent())).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Q&A（题目与回答，含评分）：\n");
        for (InterviewQuestion q : questions) {
            sb.append("Q").append(q.getOrderNo()).append("[").append(q.getType()).append("]: ").append(q.getContent()).append("\n");
            InterviewAnswer a = answers.stream().filter(x -> q.getId().equals(x.getQuestionId())).findFirst().orElse(null);
            sb.append("A: ").append(a == null ? "" : StringUtils.defaultString(a.getTextAnswer())).append("\n");
            sb.append("EvalJson: ").append(a == null ? "" : StringUtils.defaultString(a.getEvaluationJson())).append("\n\n");
        }
        return llmClient.complete(system, sb.toString()).trim();
    }

    private String buildConfigJson(Integer durationMinutes, Boolean enableCoding, Boolean enableRealtimeHints) {
        Integer d = durationMinutes == null ? 20 : durationMinutes;
        boolean coding = enableCoding != null && enableCoding;
        boolean hints = enableRealtimeHints != null && enableRealtimeHints;
        return "{\"durationMinutes\":" + d + ",\"enableCoding\":" + coding + ",\"enableRealtimeHints\":" + hints + "}";
    }

    private String buildWelcome(AiInterviewSession session) {
        return "面试开始前说明：你可以用语音回答（将自动转写并评分）。准备好后点击“下一题”。";
    }

    private byte[] toBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw new com.digital.exception.BusinessException(ErrorCode.SYSTEM_ERROR, "读取音频失败: " + e.getMessage());
        }
    }
}

