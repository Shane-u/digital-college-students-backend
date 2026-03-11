package com.digital.service.aiinterview;

import com.digital.model.entity.CandidateResume;
import com.digital.model.vo.aiinterview.ResumeUploadVO;
import com.digital.model.vo.aiinterview.ResumeVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ResumeService {

    ResumeUploadVO uploadResume(Long userId, MultipartFile file);

    ResumeVO getResume(Long userId, Long resumeId);

    CandidateResume getResumeEntity(Long userId, Long resumeId);

    /**
     * 列出用户已上传的简历
     */
    List<ResumeVO> listResumes(Long userId);
}

