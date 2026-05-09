package com.demand.system.module.llm.constant;

import java.util.List;

public final class LlmModelRole {

    private LlmModelRole() {
    }

    public static final String PRIMARY = "primary";
    public static final String HAIKU = "haiku";
    public static final String SONNET = "sonnet";
    public static final String OPUS = "opus";
    public static final String EMBEDDING = "embedding";
    public static final String RERANK = "rerank";
    public static final String GENERAL = "general";

    public static final List<String> PRESET_ROLES = List.of(PRIMARY, HAIKU, SONNET, OPUS, EMBEDDING, RERANK, GENERAL);
}
