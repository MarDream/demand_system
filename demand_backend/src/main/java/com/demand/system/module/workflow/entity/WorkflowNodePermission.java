package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("workflow_node_permissions")
public class WorkflowNodePermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowVersionId;

    private String nodeId;

    private String allowedRoles;

    private String allowedUsers;

    private String assigneeRule;

    private String visibleFields;

    private String editableFields;

    private String requiredFields;

    private String availableActions;

    private String actionConditions;

    private String notificationRules;

    private Integer timeoutHours;

    private String dataPermissions;

    private String attachmentPermissions;
}
