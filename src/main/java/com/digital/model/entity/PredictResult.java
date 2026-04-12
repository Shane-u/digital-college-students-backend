package com.digital.model.entity;

import lombok.Data;

@Data
public class PredictResult {
    private String emotion;
    private float confidence;
    private int labelIndex;
}