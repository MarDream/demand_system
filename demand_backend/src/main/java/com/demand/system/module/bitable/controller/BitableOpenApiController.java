package com.demand.system.module.bitable.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.bitable.dto.RecordQueryDTO;
import com.demand.system.module.bitable.entity.BitableApiCredential;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableFieldService;
import com.demand.system.module.bitable.service.BitableOpenApiService;
import com.demand.system.module.bitable.service.BitableRecordService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 多维表格-开放 API 集成端点。
 * 认证：X-Api-Key 请求头（格式 bk_keyId.bs_secret），凭证绑定 Base 与 scope；
 * 授权：凭证只能读取其绑定 Base 下的表；scope 控制 fields:read / records:read；
 * 限流：复用全局 RateLimitFilter；分页参数钳制，单页上限 100。
 */
@RestController
@RequestMapping("/api/v1/open/bitable/v1")
public class BitableOpenApiController {

    private final BitableOpenApiService openApiService;
    private final BitableAuthorizationService authorizationService;
    private final BitableFieldService fieldService;
    private final BitableRecordService recordService;

    public BitableOpenApiController(BitableOpenApiService openApiService,
                                    BitableAuthorizationService authorizationService,
                                    BitableFieldService fieldService,
                                    BitableRecordService recordService) {
        this.openApiService = openApiService;
        this.authorizationService = authorizationService;
        this.fieldService = fieldService;
        this.recordService = recordService;
    }

    /**
     * 获取表字段元数据（scope: fields:read）
     */
    @GetMapping("/tables/{tableId}/fields")
    public Result<?> listFields(@PathVariable Long tableId, HttpServletRequest request) {
        BitableApiCredential credential = authenticate(request);
        openApiService.requireScope(credential, "fields:read");
        requireTableInBase(credential, tableId);
        return Result.success(fieldService.listFields(tableId));
    }

    /**
     * 分页读取记录（scope: records:read）
     */
    @GetMapping("/tables/{tableId}/records")
    public Result<PageResult<?>> listRecords(@PathVariable Long tableId,
                                             @RequestParam(defaultValue = "1") Integer pageNum,
                                             @RequestParam(defaultValue = "50") Integer pageSize,
                                             HttpServletRequest request) {
        BitableApiCredential credential = authenticate(request);
        openApiService.requireScope(credential, "records:read");
        requireTableInBase(credential, tableId);

        int page = pageNum != null && pageNum > 0 ? pageNum : 1;
        int size = pageSize != null && pageSize > 0 ? Math.min(pageSize, 100) : 50;
        RecordQueryDTO query = new RecordQueryDTO();
        query.setPageNum(page);
        query.setPageSize(size);
        PageResult<?> result = recordService.queryRecords(tableId, query);
        return Result.success(result);
    }

    private BitableApiCredential authenticate(HttpServletRequest request) {
        String rawKey = request.getHeader("X-Api-Key");
        try {
            return openApiService.authenticate(rawKey);
        } catch (BusinessException e) {
            throw e;
        }
    }

    private void requireTableInBase(BitableApiCredential credential, Long tableId) {
        if (tableId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "tableId 无效");
        }
        Long baseId = authorizationService.getBaseIdByTableId(tableId);
        if (baseId == null || !baseId.equals(credential.getBaseId())) {
            // 越权访问其他 Base 的表：与不存在同语义，避免泄露
            throw new BusinessException(ErrorCode.NOT_FOUND, "数据表不存在");
        }
    }
}
