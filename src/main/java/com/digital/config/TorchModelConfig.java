package com.digital.config;

import ai.djl.ndarray.NDList;
import ai.djl.inference.Predictor;
import ai.djl.Device;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Configuration
public class TorchModelConfig {

    @Bean
    public ZooModel<NDList, NDList> model() throws Exception {
        // 使用 NDList/NDList，这样 DJL 可以使用默认的 NDList translator
        Criteria<NDList, NDList> criteria = Criteria.builder()
                .setTypes(NDList.class, NDList.class)
                // 你的环境里会触发 CUDA backend 的 op（aten::empty_strided），强制使用 CPU 避免加载失败
                .optDevice(Device.cpu())
                // PyTorch 加载时强制把权重从 CUDA 映射到 CPU
                // DJL Serving 文档里是 option.mapLocation=true，这里也用 true
                // 具体是否映射到 CPU 取决于引擎实现，但至少能避免继续走 CUDA 设备
                .optOption("mapLocation", "true")
                .optModelPath(Paths.get("emotion_java_model.pt"))
                .optModelName("emotion")
                .build();
        return criteria.loadModel();
    }

    @Bean
    public Predictor<NDList, NDList> predictor(ZooModel<NDList, NDList> model) {
        return model.newPredictor();
    }
}