package com.demand.system.module.bitable.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.bitable.service.BitableViewShareService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 多维表格-公开分享视图控制器（匿名访问）
 * 挂在 /api/v1/public/** 下（SecurityConfig 已放行），凭 token 只读访问。
 */
@RestController
@RequestMapping("/api/v1/public/bitable/views")
public class BitablePublicViewController {

    private final BitableViewShareService viewShareService;

    public BitablePublicViewController(BitableViewShareService viewShareService) {
        this.viewShareService = viewShareService;
    }

    /**
     * 获取分享视图的只读数据（脱敏）
     */
    @GetMapping("/{token}/data")
    public Result<Map<String, Object>> getPublicViewData(@PathVariable String token,
                                                         @RequestParam(defaultValue = "1") Integer pageNum,
                                                         @RequestParam(defaultValue = "50") Integer pageSize) {
        return Result.success(viewShareService.getPublicViewData(token, pageNum, pageSize));
    }
}
