package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.bitable.service.BitableFormulaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 多维表格公式控制器
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableFormulaController {

    private final BitableFormulaService bitableFormulaService;

    public BitableFormulaController(BitableFormulaService bitableFormulaService) {
        this.bitableFormulaService = bitableFormulaService;
    }

    /**
     * 校验公式（不落库）
     * 返回 valid、errorType、referencedFieldIds、resultType 等
     */
    @PostMapping("/formulas/validate")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> validateFormula(@RequestBody Map<String, Object> request) {
        String formula = (String) request.get("formula");
        Object tableIdObj = request.get("tableId");
        Long tableId = null;
        if (tableIdObj != null) {
            tableId = Long.valueOf(tableIdObj.toString());
        }

        if (formula == null || formula.isBlank()) {
            return Result.fail("公式不能为空");
        }

        Map<String, Object> result = bitableFormulaService.validateFormula(formula, tableId);
        return Result.success(result);
    }
}
