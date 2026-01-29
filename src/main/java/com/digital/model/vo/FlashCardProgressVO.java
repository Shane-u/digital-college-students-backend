package com.digital.model.vo;

import lombok.Data;

import java.util.Date;

@Data
public class FlashCardProgressVO {
    private String flashCardId;
    private Long userId; // 用于用户隔离
    private String status; // 例如："INITIALIZING", "AI_CALL_IN_PROGRESS", "PARSING_RESPONSE", "UPDATING_DATABASE", "COMPLETED", "FAILED"
    private Integer progress; // 0-100
    private String message; // 详细进度信息或错误信息
    private Date startTime;
    private Date endTime;
}
