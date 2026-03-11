package com.digital.service.aiinterview.impl;

import com.digital.config.TencentAsrConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 腾讯云一句话识别：使用 HTTP + TC3 签名直接调用，避免 SDK 的 setSkipSign NoSuchMethodError。
 */
@Service
@RequiredArgsConstructor
public class TencentAsrClientImpl implements com.digital.service.aiinterview.AsrClient {

    private static final String ASR_VERSION = "2019-06-14";
    private static final String ASR_ACTION = "SentenceRecognition";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final TencentAsrConfig config;
    private final OkHttpClient okHttpClient = new OkHttpClient.Builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AsrResult transcribe(byte[] audioBytes, String contentType) {
        AsrResult result = new AsrResult();
        if (audioBytes == null || audioBytes.length == 0) {
            result.setError("音频为空");
            return result;
        }
        if (StringUtils.isBlank(config.getSecretId()) || StringUtils.isBlank(config.getSecretKey())) {
            result.setError("腾讯云 ASR 未配置：tencent.asr.secret-id/secret-key");
            return result;
        }

        String format = inferVoiceFormat(contentType);
        String payload;
        try {
            payload = buildPayload(audioBytes, format);
        } catch (Exception e) {
            result.setError("构建请求失败: " + e.getMessage());
            return result;
        }

        long timestamp = TencentTc3Signer.currentTimestampSeconds();
        String authorization = TencentTc3Signer.authorization(config.getSecretId(), config.getSecretKey(), timestamp, payload);

        Request request = new Request.Builder()
                .url("https://" + TencentTc3Signer.hostAsr() + "/")
                .post(RequestBody.create(payload, JSON))
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("Host", TencentTc3Signer.hostAsr())
                .addHeader("X-TC-Action", ASR_ACTION)
                .addHeader("X-TC-Version", ASR_VERSION)
                .addHeader("X-TC-Timestamp", String.valueOf(timestamp))
                .addHeader("X-TC-Region", config.getRegion() != null ? config.getRegion() : "ap-shanghai")
                .addHeader("Authorization", authorization)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                result.setError("ASR 请求失败: " + response.code() + " " + body);
                return result;
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode resp = root.path("Response");
            if (resp.isMissingNode()) {
                result.setError("ASR 响应格式异常: " + body);
                return result;
            }
            JsonNode error = resp.path("Error");
            if (!error.isMissingNode() && error.path("Code").asText("").length() > 0) {
                result.setError("ASR 业务错误: " + error.path("Message").asText(""));
                return result;
            }
            String text = resp.path("Result").asText(null);
            result.setText(text != null ? text : "");
            result.setConfidence(null);
            return result;
        } catch (Exception e) {
            result.setError("腾讯云 ASR 调用异常: " + e.getMessage());
            return result;
        }
    }

    private String buildPayload(byte[] audioBytes, String voiceFormat) throws Exception {
        String data = Base64.getEncoder().encodeToString(audioBytes);
        return objectMapper.writeValueAsString(Map.of(
                "EngSerViceType", config.getEngineModelType(),
                "SourceType", 1L,
                "Data", data,
                "VoiceFormat", voiceFormat
        ));
    }

    private static String inferVoiceFormat(String contentType) {
        if (contentType == null) return "wav";
        String ct = contentType.toLowerCase();
        if (ct.contains("webm")) return "ogg-opus"; // 浏览器录音多为 webm+opus，腾讯云用 ogg-opus
        if (ct.contains("mpeg") || ct.contains("mp3")) return "mp3";
        if (ct.contains("aac")) return "aac";
        if (ct.contains("flac")) return "flac";
        if (ct.contains("ogg")) return "ogg-opus";
        if (ct.contains("m4a") || ct.contains("mp4")) return "m4a";
        if (ct.contains("wav")) return "wav";
        return "wav";
    }
}
