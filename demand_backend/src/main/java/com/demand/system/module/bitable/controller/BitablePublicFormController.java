package com.demand.system.module.bitable.controller;

import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.Result;
import com.demand.system.module.bitable.dto.PublicFormSchemaVO;
import com.demand.system.module.bitable.service.BitableFormPublishService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 多维表格-公开表单匿名访问控制器。
 * 挂在 /api/v1/public/** 下（SecurityConfig 已放行），凭 token 访问，
 * 只暴露脱敏 schema 与提交两个能力，无法枚举记录或表结构。
 */
@RestController
@RequestMapping("/api/v1/public/bitable/forms")
public class BitablePublicFormController {

    private final BitableFormPublishService formPublishService;

    public BitablePublicFormController(BitableFormPublishService formPublishService) {
        this.formPublishService = formPublishService;
    }

    /**
     * 获取表单脱敏 schema
     */
    @GetMapping("/{token}/schema")
    public Result<PublicFormSchemaVO> getSchema(@PathVariable String token) {
        PublicFormSchemaVO schema = formPublishService.getPublicSchema(token);
        if (schema == null) {
            throw new BusinessException("表单不存在或已停止收集");
        }
        return Result.success(schema);
    }

    /**
     * 匿名提交表单
     */
    @PostMapping("/{token}/submit")
    public Result<Map<String, Object>> submit(@PathVariable String token,
                                              @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<Long, Map<String, Object>> values = castValues(body.get("values"));
        String password = body.get("password") != null ? String.valueOf(body.get("password")) : null;
        return Result.success(formPublishService.submit(token, values, password));
    }

    /**
     * 前端传来 fieldId 为 JSON 对象键（字符串），转为 Long 键
     */
    private Map<Long, Map<String, Object>> castValues(Object raw) {
        Map<Long, Map<String, Object>> result = new java.util.LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                try {
                    Long fieldId = Long.parseLong(String.valueOf(entry.getKey()));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> value = (Map<String, Object>) entry.getValue();
                    result.put(fieldId, value);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }
}
