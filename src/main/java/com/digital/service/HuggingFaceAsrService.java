package com.digital.service;

import com.digital.config.HuggingFaceAsrConfig;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 调用 Hugging Face Whisper 的 ASR 服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HuggingFaceAsrService {

    private final HuggingFaceAsrConfig asrConfig;

    private final OkHttpClient okHttpClient = new OkHttpClient();

    @Data
    public static class AsrResult {
        private String text;
        private String error;
    }

    /**
     * 将音频内容发送到 Hugging Face Router API 进行转写
     * 参考官方 SDK: InferenceClient.automaticSpeechRecognition()
     *
     * @param audioBytes 音频二进制数据
     * @param contentType 音频 Content-Type，例如 audio/webm、audio/wav、audio/flac
     * @return 识别结果
     */
    public AsrResult transcribe(byte[] audioBytes, String contentType) {
        AsrResult result = new AsrResult();

        if (!StringUtils.hasText(asrConfig.getApiKey())) {
            result.setError("Hugging Face ASR API Key 未配置，请在 application.yml 中配置 huggingface.asr.api-key");
            return result;
        }

        // 构建 URL: https://router.huggingface.co/hf-inference/models/{model_id}
        // 参考官方 SDK InferenceClient，使用 hf-inference 作为 provider
        String baseUrl = asrConfig.getBaseUrl();
        // 确保 baseUrl 不以 / 结尾
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        // 使用新的 router API 格式：/hf-inference/models/{model_id}
        String url = baseUrl + "/hf-inference/models/" + asrConfig.getModel();

        // 确定 Content-Type，优先使用传入的，否则默认 audio/webm
        String mimeType = (contentType != null && !contentType.isBlank()) 
                ? contentType 
                : "audio/webm";
        MediaType mediaType = MediaType.parse(mimeType);
        
        // 直接发送音频二进制数据（与官方 SDK 行为一致）
        RequestBody body = RequestBody.create(audioBytes, mediaType);

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer " + asrConfig.getApiKey())
                .addHeader("Content-Type", mimeType)
                .build();

        log.debug("Hugging Face ASR 请求 URL: {}, Content-Type: {}, 音频大小: {} bytes", 
                url, mimeType, audioBytes.length);

        try (Response response = okHttpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String responseText = responseBody != null ? responseBody.string() : "";
            
            if (!response.isSuccessful()) {
                log.error("Hugging Face ASR 请求失败，code={} url={} body={}", 
                        response.code(), url, responseText.length() > 500 ? responseText.substring(0, 500) : responseText);
                
                // 尝试从错误响应中提取有用信息
                String errorMsg = extractErrorFromJson(responseText);
                if (errorMsg.isEmpty()) {
                    errorMsg = "ASR 请求失败: HTTP " + response.code();
                }
                result.setError(errorMsg);
                return result;
            }

            // 解析响应 JSON，提取 text 字段
            String text = extractTextFromJson(responseText);
            if (text.isEmpty() && !responseText.isBlank()) {
                // 如果解析失败，记录原始响应以便调试
                log.warn("未能从响应中提取 text 字段，原始响应: {}", 
                        responseText.length() > 200 ? responseText.substring(0, 200) : responseText);
                result.setError("响应格式异常，无法提取识别文本");
            } else {
                result.setText(text);
            }
            return result;
        } catch (IOException e) {
            log.error("调用 Hugging Face ASR 发生异常", e);
            result.setError("调用 ASR 接口异常: " + e.getMessage());
            return result;
        }
    }

    /**
     * 从 JSON 响应中提取 "text" 字段
     * 支持格式: {"text": "识别结果"} 或 {"text":"识别结果"}
     */
    private String extractTextFromJson(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        
        // 移除首尾空白
        json = json.trim();
        
        // 查找 "text" 字段
        int textIdx = json.indexOf("\"text\"");
        if (textIdx < 0) {
            return "";
        }
        
        // 找到冒号
        int colonIdx = json.indexOf(":", textIdx);
        if (colonIdx < 0) {
            return "";
        }
        
        // 跳过冒号后的空白
        int valueStart = colonIdx + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        
        if (valueStart >= json.length()) {
            return "";
        }
        
        // 检查是否是字符串值（以引号开始）
        if (json.charAt(valueStart) == '"') {
            // 提取引号内的内容
            int firstQuote = valueStart;
            int secondQuote = json.indexOf("\"", firstQuote + 1);
            if (secondQuote < 0) {
                return "";
            }
            return json.substring(firstQuote + 1, secondQuote);
        } else {
            // 可能是其他类型（如 null），返回空
            return "";
        }
    }
    
    /**
     * 从错误响应 JSON 中提取错误信息
     */
    private String extractErrorFromJson(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        
        json = json.trim();
        
        // 尝试提取 "error" 字段
        int errorIdx = json.indexOf("\"error\"");
        if (errorIdx >= 0) {
            int colonIdx = json.indexOf(":", errorIdx);
            if (colonIdx >= 0) {
                int valueStart = colonIdx + 1;
                while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
                    valueStart++;
                }
                if (valueStart < json.length() && json.charAt(valueStart) == '"') {
                    int secondQuote = json.indexOf("\"", valueStart + 1);
                    if (secondQuote > 0) {
                        return json.substring(valueStart + 1, secondQuote);
                    }
                }
            }
        }
        
        // 如果没有 error 字段，尝试提取 "message" 字段
        int msgIdx = json.indexOf("\"message\"");
        if (msgIdx >= 0) {
            int colonIdx = json.indexOf(":", msgIdx);
            if (colonIdx >= 0) {
                int valueStart = colonIdx + 1;
                while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
                    valueStart++;
                }
                if (valueStart < json.length() && json.charAt(valueStart) == '"') {
                    int secondQuote = json.indexOf("\"", valueStart + 1);
                    if (secondQuote > 0) {
                        return json.substring(valueStart + 1, secondQuote);
                    }
                }
            }
        }
        
        return "";
    }
}


