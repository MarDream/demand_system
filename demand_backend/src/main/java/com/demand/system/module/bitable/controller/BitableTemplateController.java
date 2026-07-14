package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.bitable.dto.BitableTemplateVO;
import com.demand.system.module.bitable.service.BitableTemplateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 多维表格模板库 Controller
 */
@RestController
@RequestMapping("/api/v1/bitable")
public class BitableTemplateController {

    private final BitableTemplateService bitableTemplateService;

    public BitableTemplateController(BitableTemplateService bitableTemplateService) {
        this.bitableTemplateService = bitableTemplateService;
    }

    /**
     * 列出所有预设模板
     */
    @GetMapping("/templates")
    @PreAuthorize("isAuthenticated()")
    public Result<List<BitableTemplateVO>> listTemplates() {
        List<BitableTemplateVO> list = bitableTemplateService.listTemplates();
        return Result.success(list);
    }

    /**
     * 从模板创建 Base
     *
     * @param code 模板编码（路径参数）
     * @return 新 Base 的 ID
     */
    @PostMapping("/templates/{code}/create")
    @PreAuthorize("isAuthenticated()")
    public Result<Long> createFromTemplate(@PathVariable String code) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long baseId = bitableTemplateService.createBaseFromTemplate(code, userId);
        return Result.success(baseId);
    }
}