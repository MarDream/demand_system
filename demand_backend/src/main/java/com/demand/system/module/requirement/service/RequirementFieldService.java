package com.demand.system.module.requirement.service;

import com.demand.system.module.requirement.dto.CustomFieldConfigDTO;
import com.demand.system.module.requirement.dto.CustomFieldValueDTO;
import com.demand.system.module.requirement.dto.FieldOption;
import com.demand.system.module.requirement.entity.CustomField;
import com.demand.system.module.requirement.entity.Requirement;
import com.demand.system.module.workflow.entity.WorkflowNodePermission;

import java.util.List;
import java.util.Map;

/**
 * 需求动态字段统一服务：定义读取、节点权限解析、值读写与严格校验。
 */
public interface RequirementFieldService {

    /**
     * 查询某需求类型启用的动态字段定义（含全类型通用字段）。
     */
    List<CustomField> listEnabledFields(String typeCode);

    /**
     * 解析创建场景（尚未绑定实例）的初始节点权限。
     */
    WorkflowNodePermission resolveCreatePermission(String typeCode);

    /**
     * 解析已有需求当前节点权限（版本锁定：以需求实例绑定的 workflow version 为准）。
     */
    WorkflowNodePermission resolveRequirementPermission(Requirement requirement);

    /**
     * 批量解析多个需求当前节点的权限快照（key = requirement.id）。
     * <p>供导出等批量场景使用：实例与节点权限各批量查询一次，避免逐行 N+1。
     * 无流程实例或无权限配置的需求不在结果中。
     */
    Map<Long, WorkflowNodePermission> resolvePermissions(List<Requirement> requirements);

    /** 按工作流版本和节点编码解析权限快照。 */
    WorkflowNodePermission resolvePermission(Long workflowVersionId, String nodeId);

    /**
     * 构建运行时 schema（字段定义 + 可见/可编辑权限 + 当前值）。
     */
    List<CustomFieldConfigDTO> buildSchema(List<CustomField> fields, WorkflowNodePermission permission,
                                           Map<String, CustomFieldValueDTO> currentValues);

    /**
     * 严格校验并持久化字段值（创建/编辑/流转共用）。
     *
     * @param requirement 目标需求
     * @param permission  当前节点权限
     * @param submitted   客户端提交的字段值
     */
    void validateAndPersist(Requirement requirement, WorkflowNodePermission permission,
                            List<CustomFieldValueDTO> submitted);

    /**
     * 读取某需求的动态字段当前值。
     */
    Map<String, CustomFieldValueDTO> loadValues(Long requirementId);

    /**
     * 批量读取多个需求的动态字段当前值（按需求 ID 分组，key=fieldCode）。
     * <p>供列表/导出等批量场景使用，避免逐需求查询的 N+1 问题。
     */
    Map<Long, Map<String, CustomFieldValueDTO>> loadValuesByRequirementIds(List<Long> requirementIds);

    /**
     * 解析字段选项 JSON，兼容两种形态：
     * 历史字符串数组 ["a","b"]（key=label）与对象数组 [{"key":"k","label":"l"}]。
     */
    List<FieldOption> parseFieldOptions(String optionsJson);

    /**
     * 将选项 JSON 归一化为 [{"key":"k","label":"l"}] 对象数组形态，供写库前统一存储格式。
     */
    String normalizeOptionsJson(String optionsJson);
}