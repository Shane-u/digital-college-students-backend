package com.digital.service.aiinterview;

public interface TtsClient {

    /**
     * 将文本转换为音频，返回可访问 URL（通常已上传到对象存储）。
     */
    String ttsToUrl(String text, String voice, Integer rate, Integer pitch);
}

