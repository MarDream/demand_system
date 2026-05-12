package com.demand.system.module.onlyoffice.service;

import java.util.Map;

public interface OnlyOfficeService {

    Map<String, Object> buildEditorConfig(Long knowledgeBaseId, Long documentId, Long userId, String mode);

    void handleCallback(Map<String, Object> callbackData);

    boolean checkStatus();
}
