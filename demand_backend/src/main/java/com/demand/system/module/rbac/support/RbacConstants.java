package com.demand.system.module.rbac.support;

import java.util.List;
import java.util.Set;

public final class RbacConstants {

    private RbacConstants() {
    }

    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_SUPER_ADMIN = "super_admin";
    public static final String ROLE_SUPER_ADMIN_DB = "SUPER_ADMIN";
    public static final String ROLE_WORKFLOW_CONFIG = "workflow:config";

    public static final String PERMISSION_MENU_SYSTEM_CONFIG = "menu:system-config";
    public static final String PERMISSION_MENU_SETTINGS_PROJECT = "menu:settings:project";
    public static final String PERMISSION_MENU_SETTINGS_USER = "menu:settings:user";
    public static final String PERMISSION_MENU_SETTINGS_ORG = "menu:settings:org";
    public static final String PERMISSION_MENU_SETTINGS_REQUIREMENT = "menu:settings:requirement";
    public static final String PERMISSION_MENU_SETTINGS_WORKFLOW = "menu:settings:workflow";
    public static final String PERMISSION_MENU_MANAGEMENT = "menu:menu-management";
    public static final String PERMISSION_MENU_RAG = "menu:rag";

    public static final String PERMISSION_BUTTON_MENU_CREATE = "button:menu:create";
    public static final String PERMISSION_BUTTON_MENU_UPDATE = "button:menu:update";
    public static final String PERMISSION_BUTTON_MENU_DELETE = "button:menu:delete";
    public static final String PERMISSION_BUTTON_MENU_GRANT = "button:menu:grant";
    public static final String PERMISSION_BUTTON_USER_CREATE = "button:user:create";
    public static final String PERMISSION_BUTTON_USER_UPDATE = "button:user:update";
    public static final String PERMISSION_BUTTON_USER_DELETE = "button:user:delete";
    public static final String PERMISSION_BUTTON_WORKFLOW_CONFIG = "button:workflow:config";
    public static final String PERMISSION_BUTTON_RAG_UPLOAD = "button:rag:upload";
    public static final String PERMISSION_BUTTON_RAG_SEARCH = "button:rag:search";

    public static final String PERMISSION_MENU_SETTINGS_LLM = "menu:settings:llm";
    public static final String PERMISSION_BUTTON_LLM_CREATE = "button:llm:create";
    public static final String PERMISSION_BUTTON_LLM_UPDATE = "button:llm:update";
    public static final String PERMISSION_BUTTON_LLM_DELETE = "button:llm:delete";

    public static final Set<String> PROTECTED_ROLE_CODES = Set.of(
            ROLE_SUPER_ADMIN,
            ROLE_SUPER_ADMIN_DB,
            ROLE_ADMIN
    );

    public static final List<String> ALL_PERMISSION_CODES = List.of(
            PERMISSION_MENU_SYSTEM_CONFIG,
            PERMISSION_MENU_SETTINGS_PROJECT,
            PERMISSION_MENU_SETTINGS_USER,
            PERMISSION_MENU_SETTINGS_ORG,
            PERMISSION_MENU_SETTINGS_REQUIREMENT,
            PERMISSION_MENU_SETTINGS_WORKFLOW,
            PERMISSION_MENU_MANAGEMENT,
            PERMISSION_MENU_RAG,
            PERMISSION_BUTTON_MENU_CREATE,
            PERMISSION_BUTTON_MENU_UPDATE,
            PERMISSION_BUTTON_MENU_DELETE,
            PERMISSION_BUTTON_MENU_GRANT,
            PERMISSION_BUTTON_USER_CREATE,
            PERMISSION_BUTTON_USER_UPDATE,
            PERMISSION_BUTTON_USER_DELETE,
            PERMISSION_BUTTON_WORKFLOW_CONFIG,
            PERMISSION_BUTTON_RAG_UPLOAD,
            PERMISSION_BUTTON_RAG_SEARCH,
            PERMISSION_MENU_SETTINGS_LLM,
            PERMISSION_BUTTON_LLM_CREATE,
            PERMISSION_BUTTON_LLM_UPDATE,
            PERMISSION_BUTTON_LLM_DELETE
    );
}
