package com.digital.controller.aiinterview;


import com.digital.model.entity.PredictResult;
import com.digital.service.EmotionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/emotion")
public class EmotionController {

    private final EmotionService emotionService;

    public EmotionController(EmotionService emotionService) {
        this.emotionService = emotionService;
    }

    @PostMapping("/predict")
    public PredictResult predict(@RequestParam("file") MultipartFile file) throws Exception {
        return emotionService.predict(file);
    }
}