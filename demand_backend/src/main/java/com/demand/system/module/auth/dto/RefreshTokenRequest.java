package com.demand.system.module.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新令牌请求DTO
 */
public class RefreshTokenRequest {

    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
