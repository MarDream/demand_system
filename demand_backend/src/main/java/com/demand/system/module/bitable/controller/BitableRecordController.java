package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.PageResult;
import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.BitableRecordCreateDTO;
import com.demand.system.module.bitable.dto.BitableRecordVO;
import com.demand.system.module.bitable.dto.CellUpdateDTO;
import com.demand.system.module.bitable.service.BitableRecordService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 多维表格记录控制器
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableRecordController {

    private final BitableRecordService bitableRecordService;

    public BitableRecordController(BitableRecordService bitableRecordService) {
        this.bitableRecordService = bitableRecordService;
    }

    @GetMapping("/tables/{tableId}/records")
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<BitableRecordVO>> listRecords(
            @PathVariable Long tableId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<BitableRecordVO> pageResult = bitableRecordService.listRecords(tableId, pageNum, pageSize);
        return Result.success(pageResult);
    }

    @GetMapping("/records/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<BitableRecordVO> getRecordById(@PathVariable Long id) {
        BitableRecordVO vo = bitableRecordService.getRecordById(id);
        return Result.success(vo);
    }

    @PostMapping("/tables/{tableId}/records")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> createRecord(@PathVariable Long tableId,
                                     @Valid @RequestBody BitableRecordCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long id = bitableRecordService.createRecord(tableId, dto, userId);
        return Result.success(id);
    }

    @PutMapping("/records/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> updateRecord(@PathVariable Long id,
                                     @Valid @RequestBody BitableRecordCreateDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        bitableRecordService.updateRecord(id, dto, userId);
        return Result.success();
    }

    @DeleteMapping("/records/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteRecord(@PathVariable Long id) {
        bitableRecordService.deleteRecord(id);
        return Result.success();
    }

    @PostMapping("/tables/{tableId}/records/batch")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> batchCreateRecords(@PathVariable Long tableId,
                                           @RequestBody List<BitableRecordCreateDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return Result.fail("记录列表不能为空");
        }
        Long userId = SecurityUtils.getCurrentUserId();
        Long count = bitableRecordService.batchCreateRecords(tableId, dtos, userId);
        return Result.success(count);
    }

    /**
     * 更新单个单元格值（乐观锁）
     */
    @PutMapping("/records/{recordId}/cells/{fieldId}")
    @PreAuthorize("isAuthenticated()")
    public Result<Integer> updateCell(@PathVariable Long recordId,
                                      @PathVariable Long fieldId,
                                      @RequestBody CellUpdateDTO dto) {
        if (dto.getVersion() == null) {
            return Result.fail("version 不能为空");
        }
        Long userId = SecurityUtils.getCurrentUserId();
        // 将 DTO 转为 Map 传给 Service（Service 支持接受 Map 或 CellValueDTO）
        java.util.Map<String, Object> valueMap = new java.util.HashMap<>();
        if (dto.getValueText() != null) valueMap.put("valueText", dto.getValueText());
        if (dto.getValueNumber() != null) valueMap.put("valueNumber", dto.getValueNumber());
        if (dto.getValueDate() != null) valueMap.put("valueDate", dto.getValueDate());
        if (dto.getValueJson() != null) valueMap.put("valueJson", dto.getValueJson());

        Integer newVersion = bitableRecordService.updateCell(recordId, fieldId, valueMap, dto.getVersion(), userId);
        return Result.success(newVersion);
    }
}
