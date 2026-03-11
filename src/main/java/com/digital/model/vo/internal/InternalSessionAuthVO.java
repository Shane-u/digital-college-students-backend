package com.digital.model.vo.internal;

import lombok.Data;

/**
 * Internal response for session-based authentication.
 * Used by trusted internal services (e.g., Go voice service).
 */
@Data
public class InternalSessionAuthVO {

    private Long userId;

    private String userRole;
}

