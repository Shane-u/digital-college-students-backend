package com.digital.controller;

import cn.hutool.core.io.FileUtil;
import com.digital.common.BaseResponse;
import com.digital.common.ErrorCode;
import com.digital.common.ResultUtils;
import com.digital.exception.BusinessException;
import com.digital.manager.MinioManager;
import com.digital.model.dto.file.UploadFileRequest;
import com.digital.model.entity.User;
import com.digital.model.enums.FileUploadBizEnum;
import com.digital.service.UserService;
import java.io.File;
import java.util.Arrays;


import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件接口
 */
@RestController
@RequestMapping("/file")
@Slf4j
public class  FileController {

    @Resource
    private UserService userService;

    @Resource
    private MinioManager minioManager;

    /**
     * 文件上传
     *
     * @param multipartFile
     * @param uploadFileRequest
     * @param request
     * @return
     */
    @PostMapping("/upload")
    public BaseResponse<String> uploadFile(@RequestPart("file") MultipartFile multipartFile,
            UploadFileRequest uploadFileRequest, HttpServletRequest request) {
        String biz = uploadFileRequest.getBiz();
        FileUploadBizEnum fileUploadBizEnum = FileUploadBizEnum.getEnumByValue(biz);
        if (fileUploadBizEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        validFile(multipartFile, fileUploadBizEnum);
        User loginUser = userService.getLoginUser(request);
        // 文件目录：根据业务、用户来划分
        String uuid = RandomStringUtils.randomAlphanumeric(8);
        String filename = uuid + "-" + multipartFile.getOriginalFilename();
        String objectName = String.format("%s/%s/%s", fileUploadBizEnum.getValue(), loginUser.getId(), filename);
        try {
            String url = minioManager.putObject(
                    objectName,
                    multipartFile.getInputStream(),
                    multipartFile.getContentType(),
                    multipartFile.getSize()
            );
            // 返回可访问地址（MinIO）
            return ResultUtils.success(url);
        } catch (Exception e) {
            log.error("file upload error, objectName = " + objectName, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        }
    }

    /**
     * 校验文件
     *
     * @param multipartFile
     * @param fileUploadBizEnum 业务类型
     */
    private void validFile(MultipartFile multipartFile, FileUploadBizEnum fileUploadBizEnum) {
        // 文件大小
        long fileSize = multipartFile.getSize();
        // 文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        final long ONE_M = 1024 * 1024L;
        final long ONE_HUNDRED_M = 100 * ONE_M;
        if (FileUploadBizEnum.USER_AVATAR.equals(fileUploadBizEnum)) {
            if (fileSize > ONE_M) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 1M");
            }
            if (!Arrays.asList("jpeg", "jpg", "svg", "png", "webp").contains(fileSuffix)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
        } else if (FileUploadBizEnum.AI_INTERVIEW_RESUME.equals(fileUploadBizEnum)) {
            if (fileSize > ONE_HUNDRED_M) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "简历文件大小不能超过 100M");
            }
            if (!Arrays.asList("pdf", "doc", "docx", "txt").contains(fileSuffix)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "简历文件类型仅支持 pdf/doc/docx/txt");
            }
        } else if (FileUploadBizEnum.AI_INTERVIEW_AUDIO.equals(fileUploadBizEnum)) {
            if (fileSize > ONE_HUNDRED_M) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "音频文件大小不能超过 100M");
            }
            if (!Arrays.asList("webm", "wav", "mp3", "m4a", "aac", "flac", "ogg").contains(fileSuffix)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "音频文件类型不支持");
            }
        }
    }
}
