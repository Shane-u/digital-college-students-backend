package com.digital.model.dto.aiinterview;

import lombok.Data;

import java.io.Serializable;

@Data
public class InterviewChatAppendRequest implements Serializable {
    private Long userId;
    /** user / assistant */
    private String role;
    private String content;
}
