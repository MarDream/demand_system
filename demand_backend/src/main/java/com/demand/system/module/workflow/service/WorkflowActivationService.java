package com.demand.system.module.workflow.service;

import com.demand.system.module.workflow.dto.WorkflowValidationIssue;
import com.demand.system.module.workflow.dto.WorkflowVersionDTO;

import java.util.List;

public interface WorkflowActivationService {

    WorkflowVersionDTO activate(Long versionId);

    WorkflowVersionDTO deactivate(Long versionId);

    List<WorkflowValidationIssue> validateVersion(Long versionId);
}
