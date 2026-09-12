package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.bitable.entity.BitableApiCredential;
import com.demand.system.module.bitable.mapper.BitableApiCredentialMapper;
import com.demand.system.module.bitable.service.BitableOpenApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 多维表格-开放 API 凭证 Service 实现。
 * Secret 只在创建时返回一次；比对使用 SHA-256 + 常量时间比较；
 * scope 白名单：records:read / fields:read。
 */
@Service
public class BitableOpenApiServiceImpl implements BitableOpenApiService {

    private static final Logger log = LoggerFactory.getLogger(BitableOpenApiServiceImpl.class);

    private static final Set<String> VALID_SCOPES = Set.of("records:read", "fields:read");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final BitableApiCredentialMapper credentialMapper;

    public BitableOpenApiServiceImpl(BitableApiCredentialMapper credentialMapper) {
        this.credentialMapper = credentialMapper;
    }

    @Override
    public Map<String, Object> createCredential(Long baseId, String name, List<String> scopes,
                                                LocalDateTime expireAt, Long userId) {
        Set<String> scopeSet = new LinkedHashSet<>();
        if (scopes == null || scopes.isEmpty()) {
            scopeSet.add("records:read");
        } else {
            for (String scope : scopes) {
                if (!VALID_SCOPES.contains(scope)) {
                    throw new BusinessException("不支持的授权范围: " + scope);
                }
                scopeSet.add(scope);
            }
        }

        byte[] secretBytes = new byte[32];
        SECURE_RANDOM.nextBytes(secretBytes);
        String secret = "bs_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        BitableApiCredential credential = new BitableApiCredential();
        credential.setBaseId(baseId);
        credential.setName(name != null && !name.isBlank() ? name : "API 凭证");
        credential.setKeyId("bk_" + randomToken());
        credential.setKeyHash(sha256Hex(secret));
        credential.setScopes(String.join(",", scopeSet));
        credential.setStatus("enabled");
        credential.setExpireAt(expireAt);
        credential.setCreatedBy(userId);
        credentialMapper.insert(credential);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", credential.getId());
        result.put("keyId", credential.getKeyId());
        // Secret 明文仅此处返回一次
        result.put("secret", secret);
        result.put("scopes", scopeSet);
        result.put("expireAt", credential.getExpireAt());
        log.info("开放API凭证已创建: baseId={}, keyId={}, operator={}", baseId, credential.getKeyId(), userId);
        return result;
    }

    @Override
    public List<Map<String, Object>> listByBase(Long baseId) {
        List<BitableApiCredential> credentials = credentialMapper.selectList(
                new LambdaQueryWrapper<BitableApiCredential>()
                        .eq(BitableApiCredential::getBaseId, baseId)
                        .orderByDesc(BitableApiCredential::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (BitableApiCredential credential : credentials) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", credential.getId());
            item.put("name", credential.getName());
            item.put("keyId", credential.getKeyId());
            item.put("scopes", List.of(credential.getScopes().split(",")));
            item.put("status", credential.getStatus());
            item.put("expireAt", credential.getExpireAt());
            item.put("lastUsedAt", credential.getLastUsedAt());
            item.put("createdAt", credential.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    @Override
    public void revoke(Long credentialId) {
        BitableApiCredential credential = credentialMapper.selectById(credentialId);
        if (credential == null) {
            throw new BusinessException("凭证不存在");
        }
        credentialMapper.deleteById(credentialId);
    }

    @Override
    public Long getBaseIdByCredentialId(Long credentialId) {
        if (credentialId == null) {
            return null;
        }
        BitableApiCredential credential = credentialMapper.selectById(credentialId);
        return credential != null ? credential.getBaseId() : null;
    }

    @Override
    public BitableApiCredential authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "缺少 API Key");
        }
        String[] parts = rawKey.trim().split("\\.", 2);
        if (parts.length != 2 || !parts[0].startsWith("bk_") || !parts[1].startsWith("bs_")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "API Key 格式无效");
        }
        BitableApiCredential credential = credentialMapper.selectOne(
                new LambdaQueryWrapper<BitableApiCredential>()
                        .eq(BitableApiCredential::getKeyId, parts[0]));
        if (credential == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "API Key 无效");
        }
        // 常量时间比较，防时序侧信道
        byte[] expected = hexToBytes(credential.getKeyHash());
        byte[] actual = sha256(parts[1]);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "API Key 无效");
        }
        if (!"enabled".equals(credential.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "API Key 已停用");
        }
        if (credential.getExpireAt() != null && credential.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "API Key 已过期");
        }

        // 记录最后使用时间（失败不影响本次请求）
        try {
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<BitableApiCredential> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
            wrapper.eq("id", credential.getId()).set("last_used_at", LocalDateTime.now());
            credentialMapper.update(null, wrapper);
        } catch (Exception ignored) {
        }
        return credential;
    }

    @Override
    public void requireScope(BitableApiCredential credential, String scope) {
        Set<String> scopes = new HashSet<>(Arrays.asList(credential.getScopes().split(",")));
        if (!scopes.contains(scope)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "API Key 缺少授权范围: " + scope);
        }
    }

    // ==================== 辅助 ====================

    private String randomToken() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String value) {
        return bytesToHex(sha256(value));
    }

    private byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
