package com.demand.system.module.meta.controller;

import com.demand.system.common.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 元数据接口控制器
 */
@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

    @Value("${spring.application.name:demand-system}")
    private String appName;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @GetMapping("/version")
    public Result<Map<String, String>> getVersion() {
        Map<String, String> versionInfo = new HashMap<>();
        versionInfo.put("name", appName);
        versionInfo.put("version", appVersion);
        versionInfo.put("buildTime", "2026-05-23");
        return Result.success(versionInfo);
    }
}