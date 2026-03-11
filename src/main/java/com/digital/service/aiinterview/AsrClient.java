package com.digital.service.aiinterview;

import lombok.Data;

public interface AsrClient {

    @Data
    class AsrResult {
        private String text;
        private Double confidence;
        private String error;
    }

    AsrResult transcribe(byte[] audioBytes, String contentType);
}

