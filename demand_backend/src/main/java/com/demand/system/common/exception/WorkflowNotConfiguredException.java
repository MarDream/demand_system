package com.demand.system.common.exception;

import com.demand.system.common.result.ErrorCode;

/**
 * 需求类型未配置可用工作流版本。
 *
 * 触发场景：
 *   1. {@code requirement_types.workflow_version_id} 为 NULL
 *   2. 绑定的 workflow_version 行不存在
 *   3. 绑定的 workflow_version 处于非 ACTIVE 状态（is_active!=1 或 activation_status!='active'）
 *
 * 业务影响：
 *   - 流转/审批时抛出 → 前端不应渲染"待办"操作按钮
 *   - 创建需求时抛出 → 该 type 不应出现在下拉选项中
 *
 * 与项目记忆 dual-workflow-engine-data-drift.md 中的"双轨流转引擎共用解析器"配套使用。
 */
public class WorkflowNotConfiguredException extends BusinessException {

    public static final int ERROR_CODE = ErrorCode.BAD_REQUEST;

    public WorkflowNotConfiguredException(String typeCode) {
        super(ERROR_CODE, String.format("需求类型 [%s] 未配置可用的工作流版本，请联系管理员在「需求配置 → 需求类型」中完成绑定", typeCode));
    }

    public WorkflowNotConfiguredException(String typeCode, String reason) {
        super(ERROR_CODE, String.format("需求类型 [%s] 的工作流版本不可用：%s", typeCode, reason));
    }
}
