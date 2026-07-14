package com.demand.system.module.assistant.service;

import com.demand.system.module.assistant.dto.AssistantOperationAdvice;
import com.demand.system.module.assistant.dto.AssistantPageContext;

import java.util.List;

public interface AssistantOperationCatalogService {

    AssistantOperationAdvice advise(String userMessage, AssistantPageContext pageContext, List<String> permissions, boolean superAdmin);
}
