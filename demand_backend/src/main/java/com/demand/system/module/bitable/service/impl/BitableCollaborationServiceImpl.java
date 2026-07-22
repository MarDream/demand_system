package com.demand.system.module.bitable.service.impl;

import com.demand.system.common.util.UserNameResolver;
import com.demand.system.module.bitable.constant.OperationType;
import com.demand.system.module.bitable.service.BitableCollaborationService;
import com.demand.system.module.bitable.service.BitableOperationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多维表格协作 Service 实现
 */
@Service
public class BitableCollaborationServiceImpl implements BitableCollaborationService {

    private final BitableOperationService bitableOperationService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserNameResolver userNameResolver;
    private final ObjectMapper objectMapper;

    public BitableCollaborationServiceImpl(BitableOperationService bitableOperationService,
                                           RedisTemplate<String, Object> redisTemplate,
                                           UserNameResolver userNameResolver,
                                           ObjectMapper objectMapper) {
        this.bitableOperationService = bitableOperationService;
        this.redisTemplate = redisTemplate;
        this.userNameResolver = userNameResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handleCellUpdate(Long baseId, Long tableId, Long recordId, Long fieldId, Object value, Integer newVersion, Long userId) {
        // DB 写入由 REST 路径（BitableRecordService.updateCell）统一完成，本方法仅负责协作广播，
        // 避免 REST 与 WS 双写同一条 record 的 version 乐观锁竞态（单用户编辑也会 409）。

        // 1. 记录操作日志
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("recordId", recordId);
        detail.put("fieldId", fieldId);
        detail.put("newValue", value);
        try {
            bitableOperationService.recordOperation(baseId, tableId, userId,
                    OperationType.UPDATE_CELL.getCode(),
                    objectMapper.writeValueAsString(detail));
        } catch (Exception e) {
            // 日志序列化失败不影响主流程
            bitableOperationService.recordOperation(baseId, tableId, userId,
                    OperationType.UPDATE_CELL.getCode(),
                    "{\"recordId\":" + recordId + ",\"fieldId\":" + fieldId + "}");
        }

        // 2. 广播到 Redis（由 BitableRedisSubscriber 订阅并转发给各 WebSocket 客户端）
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "cell_updated");
        message.put("tableId", tableId);
        message.put("recordId", recordId);
        message.put("fieldId", fieldId);
        message.put("value", value);
        message.put("userId", userId);
        message.put("userName", userNameResolver.resolveUserName(userId, "未知用户"));
        message.put("version", newVersion);
        redisTemplate.convertAndSend("bitable:update:" + baseId, message);
    }
}
