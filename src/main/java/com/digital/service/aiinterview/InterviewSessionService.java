package com.digital.service.aiinterview;

import com.digital.model.vo.aiinterview.AnswerVO;
import com.digital.model.vo.aiinterview.InterviewReportVO;
import com.digital.model.vo.aiinterview.InterviewReportSummaryVO;
import com.digital.model.vo.aiinterview.InterviewSessionVO;
import com.digital.model.vo.aiinterview.QuestionVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface InterviewSessionService {

    InterviewSessionVO createSession(Long userId,
                                     Long resumeId,
                                     String interviewType,
                                     String language,
                                     String difficulty,
                                     String persona,
                                     Integer durationMinutes,
                                     Boolean enableCoding,
                                     Boolean enableRealtimeHints);

    QuestionVO nextQuestion(Long userId, Long sessionId, boolean needTtsAudio);

    AnswerVO uploadAudioAnswer(Long userId,
                               Long sessionId,
                               Long questionId,
                               Integer durationSeconds,
                               MultipartFile audioFile);

    /**
     * 纯文本回答（由外部 ASR / 实时通话上报）
     */
    AnswerVO uploadTextAnswer(Long userId,
                              Long sessionId,
                              Long questionId,
                              Integer durationSeconds,
                              String textAnswer,
                              Double asrConfidence);

    InterviewReportVO finish(Long userId, Long sessionId);

    InterviewReportVO getReport(Long userId, Long sessionId);

    /**
     * 查询当前用户的历史面试报告列表（按 reportId 倒序，支持游标分页）
     *
     * @param userId 用户ID
     * @param limit 返回条数（建议 1-100）
     * @param beforeId 游标：返回 reportId < beforeId 的数据（可选）
     */
    List<InterviewReportSummaryVO> listReports(Long userId, Integer limit, Long beforeId);

    /**
     * 删除当前用户的一条面试报告（逻辑删除）
     *
     * @param userId   当前用户 ID
     * @param reportId 报告 ID
     */
    void deleteReport(Long userId, Long reportId);

    /**
     * 清空当前用户的全部面试报告（逻辑删除）
     *
     * @param userId 当前用户 ID
     */
    void clearReports(Long userId);
}

