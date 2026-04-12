package com.digital.service;

import com.digital.model.entity.PredictResult;
import org.springframework.web.multipart.MultipartFile;


public interface EmotionService {
    PredictResult predict(MultipartFile file) throws Exception;
}