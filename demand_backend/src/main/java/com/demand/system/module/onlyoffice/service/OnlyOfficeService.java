package com.demand.system.module.onlyoffice.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

public interface OnlyOfficeService {

    Map<String, Object> buildEditorConfig(Long knowledgeBaseId, Long documentId, Long userId, String mode);

    void downloadDocument(Long knowledgeBaseId, Long documentId, String accessToken,
                          HttpServletRequest request, HttpServletResponse response);

    void handleCallback(Map<String, Object> callbackData);

    boolean checkStatus();
}
