package com.digital.service.aiinterview;

import com.digital.model.vo.aiinterview.ResumeAnalysisVO;

public interface ResumeAnalysisService {

    ResumeAnalysisVO analyze(Long userId, Long resumeId, String targetRole, String targetLevel);
}

