package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.entity.BitableApiCredential;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 多维表格-开放 API 凭证 Service
 */
public interface BitableOpenApiService {

    /**
     * 创建凭证。Secret 明文仅本次返回，服务端只存 SHA-256 哈希
     *
     * @return {id, keyId, secret, scopes, expireAt}
     */
    Map<String, Object> createCredential(Long baseId, String name, List<String> scopes,
                                         LocalDateTime expireAt, Long userId);

    /** 列出 Base 下的凭证（不含哈希） */
    List<Map<String, Object>> listByBase(Long baseId);

    /** 吊销凭证 */
    void revoke(Long credentialId);

    /** 查询凭证所属的 Base（用于权限校验） */
    Long getBaseIdByCredentialId(Long credentialId);

    /**
     * 校验原始 API Key（格式 keyId.secret），通过则返回凭证
     *
     * @throws com.demand.system.common.exception.BusinessException 无效/停用/过期
     */
    BitableApiCredential authenticate(String rawKey);

    /**
     * 校验凭证具备指定 scope
     *
     * @throws com.demand.system.common.exception.BusinessException scope 不足
     */
    void requireScope(BitableApiCredential credential, String scope);
}
