package com.digital.service.aiinterview.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.digital.common.ErrorCode;
import com.digital.exception.ThrowUtils;
import com.digital.mapper.ResumeAnalysisMapper;
import com.digital.model.entity.CandidateResume;
import com.digital.model.entity.ResumeAnalysis;
import com.digital.model.vo.aiinterview.ResumeAnalysisVO;
import com.digital.service.aiinterview.LlmClient;
import com.digital.service.aiinterview.ResumeAnalysisService;
import com.digital.service.aiinterview.ResumeService;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResumeAnalysisServiceImpl implements ResumeAnalysisService {

    private final ResumeService resumeService;
    private final ResumeAnalysisMapper analysisMapper;
    private final LlmClient llmClient;

    @Override
    public ResumeAnalysisVO analyze(Long userId, Long resumeId, String targetRole, String targetLevel) {
        CandidateResume resume = resumeService.getResumeEntity(userId, resumeId);

        // 先查缓存（同 user/resume/targetRole/targetLevel）
        QueryWrapper<ResumeAnalysis> qw = new QueryWrapper<>();
        qw.eq("userId", userId).eq("resumeId", resumeId).eq("isDelete", 0);
        if (StringUtils.isNotBlank(targetRole)) {
            qw.eq("targetRole", targetRole);
        }
        if (StringUtils.isNotBlank(targetLevel)) {
            qw.eq("targetLevel", targetLevel);
        }
        ResumeAnalysis existing = analysisMapper.selectOne(qw);
        if (existing != null && StringUtils.isNotBlank(existing.getAnalysisJson())) {
            ResumeAnalysisVO vo = new ResumeAnalysisVO();
            vo.setAnalysisId(existing.getId());
            vo.setResumeId(resumeId);
            vo.setAnalysisJson(existing.getAnalysisJson());
            return vo;
        }

        String system = """
                你是资深面试官与简历评估专家。请基于候选人简历内容与目标岗位，输出严格 JSON（不要 Markdown）。
                输出字段：strengths[], weaknesses[], riskPoints[], improvementSuggestions[], suggestedQuestions[]。
                只基于简历内容推断，不要编造不存在的经历。
                """;
        StringBuilder user = new StringBuilder();
        if (StringUtils.isNotBlank(targetRole) || StringUtils.isNotBlank(targetLevel)) {
            user.append("目标岗位：").append(StringUtils.defaultString(targetRole)).append("\n");
            user.append("目标级别：").append(StringUtils.defaultString(targetLevel)).append("\n\n");
        }
        user.append("简历结构化 JSON：\n").append(StringUtils.defaultString(resume.getParsedJson())).append("\n\n");
        user.append("简历全文（可能较长）：\n").append(StringUtils.defaultString(resume.getRawText()));

        String analysisJson = llmClient.complete(system, user.toString()).trim();

        ResumeAnalysis analysis = new ResumeAnalysis();
        analysis.setUserId(userId);
        analysis.setResumeId(resumeId);
        analysis.setTargetRole(StringUtils.defaultString(targetRole));
        analysis.setTargetLevel(StringUtils.defaultString(targetLevel));
        analysis.setAnalysisJson(analysisJson);
        analysis.setCreateTime(new Date());
        analysis.setUpdateTime(new Date());
        analysisMapper.insert(analysis);

        ResumeAnalysisVO vo = new ResumeAnalysisVO();
        vo.setAnalysisId(analysis.getId());
        vo.setResumeId(resumeId);
        vo.setAnalysisJson(analysisJson);
        return vo;
    }
}

