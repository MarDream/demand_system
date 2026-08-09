package com.demand.system.module.knowledge.constant;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** AI 检索来源范围。客户端可显式指定，空值时由后端按兼容规则推断。 */
public final class KnowledgeSearchScope {
    public static final String REQUIREMENT_BODY = "REQUIREMENT_BODY";
    public static final String KNOWLEDGE_BASE = "KNOWLEDGE_BASE";
    public static final String WEB = "WEB";

    private KnowledgeSearchScope() {
    }

    public static Set<String> normalize(Collection<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return Set.of();
        }
        return scopes.stream()
                .filter(scope -> scope != null && !scope.isBlank())
                .map(scope -> scope.trim().toUpperCase(Locale.ROOT))
                .filter(scope -> REQUIREMENT_BODY.equals(scope) || KNOWLEDGE_BASE.equals(scope) || WEB.equals(scope))
                .collect(Collectors.toUnmodifiableSet());
    }
}
