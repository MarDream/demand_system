package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.BitableFieldCreateDTO;
import com.demand.system.module.bitable.dto.BitableFieldUpdateDTO;
import com.demand.system.module.bitable.dto.BitableFieldVO;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableFieldService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 多维表格字段控制器
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableFieldController {

    private final BitableFieldService bitableFieldService;
    private final BitableAuthorizationService authorizationService;

    public BitableFieldController(BitableFieldService bitableFieldService,
                                  BitableAuthorizationService authorizationService) {
        this.bitableFieldService = bitableFieldService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/tables/{tableId}/fields")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableFieldVO>> listFields(@PathVariable Long tableId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByTableId(tableId);
        authorizationService.checkReadPermission(baseId, userId);
        List<BitableFieldVO> list = bitableFieldService.listFields(tableId);
        return Result.success(list);
    }

    @PostMapping("/tables/{tableId}/fields")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> createField(@PathVariable Long tableId,
                                    @Valid @RequestBody BitableFieldCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByTableId(tableId);
        authorizationService.checkManagePermission(baseId, userId);
        Long id = bitableFieldService.createField(tableId, dto);
        return Result.success(id);
    }

    @PutMapping("/fields/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> updateField(@PathVariable Long id, @RequestBody BitableFieldUpdateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByFieldId(id);
        authorizationService.checkManagePermission(baseId, userId);
        bitableFieldService.updateField(id, dto);
        return Result.success();
    }

    @DeleteMapping("/fields/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteField(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByFieldId(id);
        authorizationService.checkManagePermission(baseId, userId);
        bitableFieldService.deleteField(id);
        return Result.success();
    }

    @PutMapping("/tables/{tableId}/fields/sort")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> sortFields(@PathVariable Long tableId, @RequestBody List<Long> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) {
            return Result.fail("字段ID列表不能为空");
        }
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = authorizationService.getBaseIdByTableId(tableId);
        authorizationService.checkManagePermission(baseId, userId);
        bitableFieldService.sortFields(tableId, fieldIds);
        return Result.success();
    }
}
