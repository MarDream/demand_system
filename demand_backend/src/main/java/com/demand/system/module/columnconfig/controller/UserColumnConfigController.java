package com.demand.system.module.columnconfig.controller;

import com.demand.system.common.result.Result;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.columnconfig.service.UserColumnConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/column-config")
public class UserColumnConfigController {

    private final UserColumnConfigService service;

    public UserColumnConfigController(UserColumnConfigService service) {
        this.service = service;
    }

    @GetMapping("/{pageKey}")
    public Result<List<String>> getConfig(@PathVariable String pageKey) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<String> columns = service.getVisibleColumns(userId, pageKey);
        return Result.success(columns);
    }

    @PutMapping("/{pageKey}")
    public Result<Void> saveConfig(@PathVariable String pageKey, @RequestBody Map<String, List<String>> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        service.saveVisibleColumns(userId, pageKey, body.get("columns"));
        return Result.success(null);
    }
}