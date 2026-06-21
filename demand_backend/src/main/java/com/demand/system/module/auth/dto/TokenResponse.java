package com.demand.system.module.auth.dto;

/**
 * Token响应DTO
 */
public class TokenResponse {

    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private String tokenType;
    /** 首次登录或无组织用户需强制选择组织 */
    private Boolean needOrgBind;

    public TokenResponse() {
    }

    public TokenResponse(String accessToken, String refreshToken, Long expiresIn, String tokenType) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.tokenType = tokenType;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Boolean getNeedOrgBind() {
        return needOrgBind;
    }

    public void setNeedOrgBind(Boolean needOrgBind) {
        this.needOrgBind = needOrgBind;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String accessToken;
        private String refreshToken;
        private Long expiresIn;
        private String tokenType;
        private Boolean needOrgBind;

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public Builder expiresIn(Long expiresIn) {
            this.expiresIn = expiresIn;
            return this;
        }

        public Builder tokenType(String tokenType) {
            this.tokenType = tokenType;
            return this;
        }

        public Builder needOrgBind(Boolean needOrgBind) {
            this.needOrgBind = needOrgBind;
            return this;
        }

        public TokenResponse build() {
            TokenResponse response = new TokenResponse(accessToken, refreshToken, expiresIn, tokenType);
            response.setNeedOrgBind(needOrgBind);
            return response;
        }
    }
}
