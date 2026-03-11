package com.digital.service.aiinterview.impl;

import com.digital.common.ErrorCode;
import com.digital.config.LibreTtsConfig;
import com.digital.exception.BusinessException;
import com.digital.exception.ThrowUtils;
import com.digital.service.aiinterview.AiInterviewFileStorageService;
import com.digital.service.aiinterview.TtsClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LibreTtsClientImpl implements TtsClient {

    private final LibreTtsConfig config;

    private final AiInterviewFileStorageService fileStorageService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient okHttpClient = new OkHttpClient();

    @Override
    public String ttsToUrl(String text, String voice, Integer rate, Integer pitch) {
        ThrowUtils.throwIf(StringUtils.isBlank(text), ErrorCode.PARAMS_ERROR, "text 不能为空");

        String usedVoice = StringUtils.isBlank(voice) ? config.getVoice() : voice;
        int usedRate = rate == null ? (config.getRate() == null ? 0 : config.getRate()) : rate;
        int usedPitch = pitch == null ? (config.getPitch() == null ? 0 : config.getPitch()) : pitch;

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("text", text);
        bodyMap.put("voice", usedVoice);
        bodyMap.put("rate", usedRate);
        bodyMap.put("pitch", usedPitch);
        bodyMap.put("preview", config.getPreview() != null && config.getPreview());

        try {
            String json = objectMapper.writeValueAsString(bodyMap);
            RequestBody reqBody = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
            Request req = new Request.Builder()
                    .url(config.getBaseUrl())
                    .post(reqBody)
                    .build();

            try (Response resp = okHttpClient.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    String err = "";
                    ResponseBody rb = resp.body();
                    if (rb != null) {
                        err = rb.string();
                    }
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "TTS 调用失败: HTTP " + resp.code() + " " + err);
                }
                byte[] audioBytes;
                ResponseBody rb = resp.body();
                if (rb == null) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "TTS 响应为空");
                }
                audioBytes = rb.bytes();

                // 这里为了复用已有 COS 上传逻辑：将音频作为 MultipartFile 不方便，
                // 采用写临时文件再上传（由 fileStorageService 内部完成上传）。
                // 但 fileStorageService 目前接收 MultipartFile，因此这里先返回 data:，由上层决定是否需要存储。
                // 为了让接口可用：先直接返回 base64 dataURL（前端可直接播放），后续可再优化为上传 COS。
                // 由于 dataURL 过大可能影响性能，建议后续迭代改为对象存储 URL。
                String base64 = java.util.Base64.getEncoder().encodeToString(audioBytes);
                return "data:audio/wav;base64," + base64;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "TTS 调用异常: " + e.getMessage());
        }
    }
}

