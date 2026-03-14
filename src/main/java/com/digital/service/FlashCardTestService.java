package com.digital.service;

import com.digital.model.dto.flashcard.FlashCardTestGenerateRequest;
import com.digital.model.dto.flashcard.FlashCardTestSubmitRequest;
import com.digital.model.vo.FlashCardTestResultVO;
import com.digital.model.vo.FlashCardTestAttemptSummaryVO;
import com.digital.model.vo.FlashCardTestPaperSummaryVO;
import com.digital.model.vo.FlashCardTestQuestionResultVO;
import com.digital.model.vo.FlashCardTestVO;
import java.util.List;

/**
 * 闪卡 AI 测试服务
 */
public interface FlashCardTestService {

    /**
     * 生成测试题（调用豆包并落库）
     */
    FlashCardTestVO generateTest(Long userId, FlashCardTestGenerateRequest request);

    /**
     * 提交并批改测试，完成点亮与成长轨迹同步
     */
    FlashCardTestResultVO submitAndCorrect(Long userId, FlashCardTestSubmitRequest request);

    /**
     * 列出某个闪卡节点下的所有历史试卷（一次 generate 生成一套题）
     */
    List<FlashCardTestPaperSummaryVO> listPapers(Long userId, String nodeId, String difficulty);

    /**
     * 获取某套试卷的题目（按“历史最高分”聚合每题得分）
     */
    List<FlashCardTestQuestionResultVO> getPaperQuestionsWithBestScore(Long userId, Long testId);

    /**
     * 获取某套试卷的提交历史（每次 submit 一条记录）
     */
    List<FlashCardTestAttemptSummaryVO> listAttempts(Long userId, Long testId);

    /**
     * 获取某次提交（attempt）的逐题批改明细快照（可回放当次作答）
     */
    List<FlashCardTestQuestionResultVO> getAttemptDetail(Long userId, Long attemptId);

    /**
     * 加载历史试卷题目（不带历史作答/得分），用于重做
     */
    FlashCardTestVO loadPaper(Long userId, Long testId);

    /**
     * 删除试卷（逻辑删除 MySQL 主表+题目+提交历史，并删除 Neo4j 测试点节点；删除后重算闪卡点亮）
     */
    void deletePaper(Long userId, Long testId);
}

