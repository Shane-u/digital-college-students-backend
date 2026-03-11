package com.digital.service.aiinterview.impl;

import com.digital.exception.ThrowUtils;
import com.digital.common.ErrorCode;
import com.digital.manager.MinioManager;
import com.digital.service.aiinterview.AiInterviewFileStorageService;
import java.io.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiInterviewFileStorageServiceImpl implements AiInterviewFileStorageService {

    private final MinioManager minioManager;

    @Override
    public String uploadResumeFile(Long userId, MultipartFile file) {
        return upload(userId, "ai_interview_resume", file);
    }

    @Override
    public String uploadAudioFile(Long userId, MultipartFile file) {
        return upload(userId, "ai_interview_audio", file);
    }

    private String upload(Long userId, String biz, MultipartFile multipartFile) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "用户 ID 不能为空");
        ThrowUtils.throwIf(multipartFile == null || multipartFile.isEmpty(), ErrorCode.PARAMS_ERROR, "上传文件为空");

        String uuid = RandomStringUtils.randomAlphanumeric(8);
        String originalFilename = multipartFile.getOriginalFilename();
        String filename = uuid + "-" + (originalFilename == null ? "file" : originalFilename);
        String objectName = String.format("%s/%s/%s", biz, userId, filename);
        try {
            String url = minioManager.putObject(
                    objectName,
                    multipartFile.getInputStream(),
                    multipartFile.getContentType(),
                    multipartFile.getSize()
            );
            return url;
        } catch (Exception e) {
            log.error("上传文件失败 objectName={}", objectName, e);
            throw new com.digital.exception.BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        }
    }
}

