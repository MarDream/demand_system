package com.demand.system.module.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenResponse {

    private String accessToken;

    private String refreshToken;

    private Long expiresIn;

    private String tokenType;
}
