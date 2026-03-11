package com.digital.controller.internal;

import com.digital.common.BaseResponse;
import com.digital.common.ErrorCode;
import com.digital.common.ResultUtils;
import com.digital.model.entity.User;
import com.digital.model.vo.internal.InternalSessionAuthVO;
import com.digital.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal-only auth endpoints for trusted services.
 */
@RestController
@RequestMapping("/internal/auth")
@Slf4j
public class InternalAuthController {

    @Resource
    private UserService userService;

    /**
     * Shared secret to protect internal endpoints.
     * Configure via internal.auth.token (recommended from env).
     */
    @Value("${internal.auth.token:}")
    private String internalAuthToken;

    @GetMapping("/session")
    public BaseResponse<InternalSessionAuthVO> session(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            HttpServletRequest request
    ) {
        if (StringUtils.isBlank(internalAuthToken)) {
            log.warn("internal.auth.token is blank; refusing internal auth request");
            return new BaseResponse<>(ErrorCode.FORBIDDEN_ERROR.getCode(), null, "internal auth disabled");
        }
        if (!internalAuthToken.equals(token)) {
            return new BaseResponse<>(ErrorCode.NO_AUTH_ERROR.getCode(), null, "invalid internal token");
        }

        User user = userService.getLoginUserPermitNull(request);
        if (user == null || user.getId() == null) {
            return new BaseResponse<>(ErrorCode.NOT_LOGIN_ERROR.getCode(), null, ErrorCode.NOT_LOGIN_ERROR.getMessage());
        }

        InternalSessionAuthVO vo = new InternalSessionAuthVO();
        vo.setUserId(user.getId());
        vo.setUserRole(user.getUserRole());
        return ResultUtils.success(vo);
    }
}

