package com.digital.service.aiinterview;

import org.springframework.web.multipart.MultipartFile;

public interface AiInterviewFileStorageService {

    String uploadResumeFile(Long userId, MultipartFile file);

    String uploadAudioFile(Long userId, MultipartFile file);
}

