package com.demand.system.module.auth.service;

import com.demand.system.module.auth.dto.LoginRequest;
import com.demand.system.module.auth.dto.RefreshTokenRequest;
import com.demand.system.module.auth.dto.TokenResponse;
import com.demand.system.module.auth.dto.UserInfoResponse;

public interface AuthService {

    TokenResponse login(LoginRequest request);

    void logout(String token);

    TokenResponse refreshToken(RefreshTokenRequest request);

    UserInfoResponse getCurrentUser();
}
