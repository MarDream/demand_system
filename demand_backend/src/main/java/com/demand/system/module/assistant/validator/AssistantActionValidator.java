package com.demand.system.module.assistant.validator;

import com.demand.system.module.assistant.dto.AssistantAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class AssistantActionValidator {

    public List<AssistantAction> sanitize(List<AssistantAction> actions, List<String> permissions, boolean superAdmin) {
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }
        List<AssistantAction> result = new ArrayList<>();
        Set<String> deduplicated = new LinkedHashSet<>();
        for (AssistantAction action : actions) {
            if (action == null) {
                continue;
            }
            if (action.getType() == null || action.getType().isBlank()) {
                continue;
            }
            if ("NAVIGATE".equalsIgnoreCase(action.getType())) {
                if (action.getTargetPath() == null || !action.getTargetPath().startsWith("/")) {
                    continue;
                }
                if (!superAdmin && action.getPermission() != null && !action.getPermission().isBlank()
                        && (permissions == null || !permissions.contains(action.getPermission()))) {
                    continue;
                }
            }
            String key = action.getType() + "::" + action.getTargetPath() + "::" + action.getLabel();
            if (deduplicated.add(key)) {
                result.add(action);
            }
        }
        return result;
    }
}
