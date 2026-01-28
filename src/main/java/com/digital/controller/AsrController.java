package com.digital.controller;

import com.digital.common.BaseResponse;
import com.digital.common.ResultUtils;
import com.digital.service.HuggingFaceAsrService;
import com.digital.service.HuggingFaceAsrService.AsrResult;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 通用语音识别（ASR）控制器
 * 前端负责录音并上传音频，后端调用 Hugging Face Whisper 进行转写
 */
@RestController
@RequestMapping("/asr")
@Slf4j
public class AsrController {

    @Resource
    private HuggingFaceAsrService asrService;

    @Data
    public static class AsrResponse {
        private String text;
        private String error;
    }

    /**
     * 接收前端上传的音频文件并进行语音转文字
     *
     * @param file 录音音频文件（前端通过 FormData 以 file 字段上传）
     * @return 识别结果
     */
    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<AsrResponse> transcribe(@RequestPart("file") MultipartFile file) {
        AsrResponse response = new AsrResponse();

        if (file == null || file.isEmpty()) {
            response.setError("上传的音频文件为空");
            return ResultUtils.success(response);
        }

        try {
            String contentType = file.getContentType();
            byte[] bytes = file.getBytes();

            AsrResult result = asrService.transcribe(bytes, contentType);
            response.setText(result.getText());
            response.setError(result.getError());

            return ResultUtils.success(response);
        } catch (IOException e) {
            log.error("读取上传音频文件失败", e);
            response.setError("读取音频文件失败: " + e.getMessage());
            return ResultUtils.success(response);
        }
    }
}


