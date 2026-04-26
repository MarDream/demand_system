package com.demand.system.module.workflow.service;

import com.demand.system.module.workflow.dto.TransitionResponse;
import com.demand.system.module.workflow.entity.WorkflowState;
import com.demand.system.module.workflow.entity.WorkflowTransition;
import com.demand.system.module.workflow.entity.WorkflowVersion;

import java.util.List;

public interface WorkflowService {

    List<WorkflowState> getStates(Long projectId);

    WorkflowState createState(Long projectId, WorkflowState state);

    void updateState(Long id, WorkflowState state);

    void deleteState(Long id);

    List<WorkflowTransition> getTransitions(Long projectId);

    WorkflowTransition createTransition(Long projectId, WorkflowTransition transition);

    void updateTransition(Long id, WorkflowTransition transition);

    void deleteTransition(Long id);

    List<WorkflowTransition> getAvailableTransitions(Long requirementId, Long userId);

    TransitionResponse executeTransition(Long requirementId, Long targetStateId, Long userId, String comment);

    List<WorkflowVersion> getVersions(Long projectId);

    void createVersion(WorkflowVersion version);

    void updateVersion(Long id, WorkflowVersion version);

    void activateVersion(Long id, Long projectId);

    List<String> validateWorkflow(String definition);
}
