package com.demand.system.module.bitable.util;

import com.demand.system.module.bitable.constant.OperationType;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableOperationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 多维表格审计助手：统一封装关键写操作的操作日志写入。
 * 审计失败只记录告警，不影响业务主流程。
 */
@Component
public class BitableAuditHelper {

    private static final Logger log = LoggerFactory.getLogger(BitableAuditHelper.class);

    private final BitableOperationService operationService;
    private final BitableAuthorizationService authorizationService;

    public BitableAuditHelper(BitableOperationService operationService,
                              BitableAuthorizationService authorizationService) {
        this.operationService = operationService;
        this.authorizationService = authorizationService;
    }

    /**
     * 已知 baseId 时直接写审计
     */
    public void record(Long baseId, Long tableId, Long userId, OperationType type, String detail) {
        try {
            operationService.recordOperation(baseId, tableId, userId, type.getCode(), detail);
        } catch (Exception e) {
            log.warn("审计日志写入失败: baseId={}, tableId={}, type={}", baseId, tableId, type.getCode(), e);
        }
    }

    /**
     * 通过 tableId 反查 baseId 后写审计
     */
    public void recordByTable(Long tableId, Long userId, OperationType type, String detail) {
        try {
            Long baseId = authorizationService.getBaseIdByTableId(tableId);
            if (baseId != null) {
                record(baseId, tableId, userId, type, detail);
            }
        } catch (Exception e) {
            log.warn("审计日志写入失败: tableId={}, type={}", tableId, type.getCode(), e);
        }
    }
}
