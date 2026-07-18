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
    public static final String PERMISSION_MENU_SETTINGS_REQUIREMENT = "menu:settings:requirement";
    public static final String PERMISSION_MENU_SETTINGS_WORKFLOW = "menu:settings:workflow";
    public static final String PERMISSION_MENU_SETTINGS_ROLE = "menu:settings:role";
    public static final String PERMISSION_MENU_MANAGEMENT = "menu:menu-management";
    public static final String PERMISSION_MENU_DOCUMENT = "menu:document";
    public static final String PERMISSION_MENU_KNOWLEDGE = "menu:knowledge";

    public static final String PERMISSION_BUTTON_MENU_CREATE = "button:menu:create";
    public static final String PERMISSION_BUTTON_MENU_UPDATE = "button:menu:update";
    public static final String PERMISSION_BUTTON_MENU_DELETE = "button:menu:delete";
    public static final String PERMISSION_BUTTON_MENU_GRANT = "button:menu:grant";
    public static final String PERMISSION_BUTTON_USER_CREATE = "button:user:create";
    public static final String PERMISSION_BUTTON_USER_UPDATE = "button:user:update";
    public static final String PERMISSION_BUTTON_USER_DELETE = "button:user:delete";
    public static final String PERMISSION_BUTTON_ROLE_CREATE = "button:role:create";
    public static final String PERMISSION_BUTTON_ROLE_UPDATE = "button:role:update";
    public static final String PERMISSION_BUTTON_ROLE_DELETE = "button:role:delete";
    public static final String PERMISSION_BUTTON_ROLE_GRANT = "button:role:grant";
    public static final String PERMISSION_BUTTON_WORKFLOW_CONFIG = "button:workflow:config";
    public static final String PERMISSION_BUTTON_RAG_UPLOAD = "button:rag:upload";
    public static final String PERMISSION_BUTTON_RAG_SEARCH = "button:rag:search";

    public static final String PERMISSION_MENU_SETTINGS_LLM = "menu:settings:llm";
    public static final String PERMISSION_BUTTON_LLM_CREATE = "button:llm-provider:create";
    public static final String PERMISSION_BUTTON_LLM_UPDATE = "button:llm-provider:update";
    public static final String PERMISSION_BUTTON_LLM_DELETE = "button:llm-provider:delete";
    public static final String PERMISSION_BUTTON_LLM_TEST = "button:llm-provider:test";

    public static final Set<String> PROTECTED_ROLE_CODES = Set.of(
            ROLE_SUPER_ADMIN,
            ROLE_SUPER_ADMIN_DB,
            ROLE_ADMIN
    );

    public static final String PERMISSION_MENU_DASHBOARD = "menu:dashboard";
    public static final String PERMISSION_MENU_REQUIREMENT = "menu:requirement";
    public static final String PERMISSION_MENU_ITERATION = "menu:iteration";
    public static final String PERMISSION_MENU_BITABLE = "menu:bitable";
    public static final String PERMISSION_BUTTON_BITABLE_CREATE = "button:bitable:create";
    public static final String PERMISSION_BUTTON_BITABLE_UPDATE = "button:bitable:update";
    public static final String PERMISSION_BUTTON_BITABLE_DELETE = "button:bitable:delete";

    public static final List<String> ALL_PERMISSION_CODES = List.of(
            PERMISSION_MENU_DASHBOARD,
            PERMISSION_MENU_REQUIREMENT,
            PERMISSION_MENU_ITERATION,
            PERMISSION_MENU_BITABLE,
            PERMISSION_MENU_SYSTEM_CONFIG,
            PERMISSION_MENU_SETTINGS_PROJECT,
            PERMISSION_MENU_SETTINGS_USER,
            PERMISSION_MENU_SETTINGS_REQUIREMENT,
            PERMISSION_MENU_SETTINGS_WORKFLOW,
            PERMISSION_MENU_SETTINGS_ROLE,
            PERMISSION_MENU_MANAGEMENT,
            PERMISSION_MENU_DOCUMENT,
            PERMISSION_MENU_KNOWLEDGE,
            PERMISSION_BUTTON_MENU_CREATE,
            PERMISSION_BUTTON_MENU_UPDATE,
            PERMISSION_BUTTON_MENU_DELETE,
            PERMISSION_BUTTON_MENU_GRANT,
            PERMISSION_BUTTON_USER_CREATE,
            PERMISSION_BUTTON_USER_UPDATE,
            PERMISSION_BUTTON_USER_DELETE,
            PERMISSION_BUTTON_ROLE_CREATE,
            PERMISSION_BUTTON_ROLE_UPDATE,
            PERMISSION_BUTTON_ROLE_DELETE,
            PERMISSION_BUTTON_ROLE_GRANT,
            PERMISSION_BUTTON_WORKFLOW_CONFIG,
            PERMISSION_BUTTON_RAG_UPLOAD,
            PERMISSION_BUTTON_RAG_SEARCH,
            PERMISSION_MENU_SETTINGS_LLM,
            PERMISSION_BUTTON_LLM_CREATE,
            PERMISSION_BUTTON_LLM_UPDATE,
            PERMISSION_BUTTON_LLM_DELETE,
            PERMISSION_BUTTON_LLM_TEST,
            "menu:requirement:view:all",
            "menu:requirement:view:pending",
            "menu:requirement:view:done",
            "menu:requirement:view:draft",
            "button:requirement:create",
            "button:requirement:update",
            "button:requirement:delete",
            "button:requirement:export",
            "button:requirement:submit",
            "button:requirement:split",
            "button:requirement:comment",
            "button:requirement:rollback",
            "button:requirement:cancel",
            "button:requirement:batch-delete",
            PERMISSION_BUTTON_BITABLE_CREATE,
            PERMISSION_BUTTON_BITABLE_UPDATE,
            PERMISSION_BUTTON_BITABLE_DELETE
    );
}
