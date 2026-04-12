package com.digital.service.impl;

import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import org.springframework.beans.factory.ObjectProvider;

import com.digital.model.entity.PredictResult;
import com.digital.service.EmotionService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
public class EmotionServiceImpl implements EmotionService {

    private static final String[] LABELS = {
            "angry", "disgust", "fear", "happy", "sad", "surprise", "neutral"
    };

    // 延迟加载模型，避免模型初始化失败导致整个 Spring 上下文启动失败
    private final ObjectProvider<Predictor<NDList, NDList>> predictorProvider;

    public EmotionServiceImpl(ObjectProvider<Predictor<NDList, NDList>> predictorProvider) {
        this.predictorProvider = predictorProvider;
    }

    @Override
    public PredictResult predict(MultipartFile file) throws Exception {
        // 1. 读取图片
        InputStream in = new ByteArrayInputStream(file.getBytes());
        BufferedImage image = ImageIO.read(in);
        if (image == null) {
            throw new IllegalArgumentException("Failed to read image from upload");
        }

        // 2. 转为 48x48 灰度图
        BufferedImage grayImg = new BufferedImage(48, 48, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = grayImg.createGraphics();
        g2d.drawImage(image, 0, 0, 48, 48, null);
        g2d.dispose();

        // 3. 构造模型输入 [1,3,48,48]（batch=1）
        // DJL 的 NDManager.create 在此处不支持 float[][][][]，因此先展平为 float[]
        float[] inputFlat = new float[1 * 3 * 48 * 48];
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 48; x++) {
                float px = grayImg.getRaster().getSample(x, y, 0) / 255.0f;
                int base = (y * 48 + x);
                inputFlat[0 * 3 * 48 * 48 + 0 * 48 * 48 + base] = px;
                inputFlat[0 * 3 * 48 * 48 + 1 * 48 * 48 + base] = px;
                inputFlat[0 * 3 * 48 * 48 + 2 * 48 * 48 + base] = px;
            }
        }

        // 4. 推理（NDList -> NDList）
        Predictor<NDList, NDList> predictor = predictorProvider.getIfAvailable();
        if (predictor == null) {
            throw new IllegalStateException("Emotion model is not available (predictor bean not loaded)");
        }

        try (NDManager manager = NDManager.newBaseManager()) {
            NDArray inputNd = manager.create(inputFlat).reshape(new Shape(1, 3, 48, 48));
            NDList inputList = new NDList(inputNd);
            NDList output = predictor.predict(inputList);

            // 5. 找最大值（假设分类输出为 [1,7] 或展平后长度>=7）
            NDArray outArray = output.singletonOrThrow();
            float[] outputArr = outArray.toFloatArray();
            if (outputArr.length < LABELS.length) {
                throw new IllegalStateException("Model output size is smaller than label size");
            }

        // 模型最后一层是 logits：先用 argmax 找类别，再对 logits 做 softmax 得到概率置信度
        int idx = 0;
        float maxLogit = outputArr[0];
        for (int i = 1; i < outputArr.length; i++) {
            if (outputArr[i] > maxLogit) {
                maxLogit = outputArr[i];
                idx = i;
            }
        }
        if (idx >= LABELS.length) {
            throw new IllegalStateException("Model predicted index out of label range: " + idx);
        }

        // 数值稳定：softmax(x) = exp(x-max) / sum(exp(x-max))
        double sumExp = 0.0;
        for (float logit : outputArr) {
            sumExp += Math.exp(logit - maxLogit);
        }
        float confidence = (float) (Math.exp(outputArr[idx] - maxLogit) / sumExp);

            PredictResult res = new PredictResult();
            res.setEmotion(LABELS[idx]);
        res.setConfidence(confidence);
            res.setLabelIndex(idx);
            return res;
        }
    }
}
