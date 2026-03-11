package com.digital.service.aiinterview;

import com.digital.model.vo.aiinterview.AnswerVO;
import com.digital.model.vo.aiinterview.InterviewReportVO;
import com.digital.model.vo.aiinterview.InterviewSessionVO;
import com.digital.model.vo.aiinterview.QuestionVO;
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
}

