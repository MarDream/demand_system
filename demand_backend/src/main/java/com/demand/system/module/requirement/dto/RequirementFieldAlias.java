package com.demand.system.module.requirement.dto;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RequirementFieldAlias {

    private static final Map<String, String> FIELD_MAP = new LinkedHashMap<>();

    static {
        FIELD_MAP.put("title", "title");
        FIELD_MAP.put("标题", "title");
        FIELD_MAP.put("description", "description");
        FIELD_MAP.put("描述", "description");
        FIELD_MAP.put("type", "type");
        FIELD_MAP.put("需求类型", "type");
        FIELD_MAP.put("priority", "priority");
        FIELD_MAP.put("优先级", "priority");
        FIELD_MAP.put("assigneeId", "assigneeId");
        FIELD_MAP.put("负责人", "assigneeId");
        FIELD_MAP.put("moduleId", "moduleId");
        FIELD_MAP.put("模块", "moduleId");
        FIELD_MAP.put("iterationId", "iterationId");
        FIELD_MAP.put("所属迭代", "iterationId");
        FIELD_MAP.put("startDate", "startDate");
        FIELD_MAP.put("开始时间", "startDate");
        FIELD_MAP.put("开始日期", "startDate");
        FIELD_MAP.put("dueDate", "dueDate");
        FIELD_MAP.put("截止时间", "dueDate");
        FIELD_MAP.put("截止日期", "dueDate");
        FIELD_MAP.put("estimatedHours", "estimatedHours");
        FIELD_MAP.put("估算工时", "estimatedHours");
        FIELD_MAP.put("actualHours", "actualHours");
        FIELD_MAP.put("实际工时", "actualHours");
        FIELD_MAP.put("attachments", "attachments");
        FIELD_MAP.put("附件", "attachments");
    }

    private RequirementFieldAlias() {
    }

    public static String normalize(String rawField) {
        if (!StringUtils.hasText(rawField)) {
            return null;
        }
        String normalized = FIELD_MAP.get(rawField.trim());
        return StringUtils.hasText(normalized) ? normalized : rawField.trim();
    }
}
