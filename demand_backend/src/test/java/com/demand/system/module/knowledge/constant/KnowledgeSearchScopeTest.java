package com.demand.system.module.knowledge.constant;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeSearchScopeTest {

    @Test
    void normalizesSupportedScopesAndDropsInvalidValues() {
        assertEquals(Set.of(
                KnowledgeSearchScope.REQUIREMENT_BODY,
                KnowledgeSearchScope.KNOWLEDGE_BASE,
                KnowledgeSearchScope.WEB),
                KnowledgeSearchScope.normalize(List.of(" requirement_body ", "KNOWLEDGE_BASE", "web", "INVALID")));
    }

    @Test
    void nullOrEmptyScopesRemainEmptyForCompatibilityDecision() {
        assertEquals(Set.of(), KnowledgeSearchScope.normalize(null));
        assertEquals(Set.of(), KnowledgeSearchScope.normalize(List.of()));
    }
}
