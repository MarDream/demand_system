package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.dto.BitableAutomationCreateDTO;
import com.demand.system.module.bitable.dto.BitableAutomationUpdateDTO;
import com.demand.system.module.bitable.dto.BitableAutomationVO;

import java.util.List;
import java.util.Map;

/**
 * 多维表格-自动化规则 Service
 */
public interface BitableAutomationService {

    /**
     * 列出指定多维表格下的自动化规则
     *
     * @param baseId 多维表格ID
     * @return 自动化规则VO列表
     */
    List<BitableAutomationVO> listAutomations(Long baseId);

    /**
     * 创建自动化规则
     *
     * @param baseId 多维表格ID
     * @param dto    创建参数
     * @param userId 创建人ID
     * @return 新规则ID
     */
    Long createAutomation(Long baseId, BitableAutomationCreateDTO dto, Long userId);

    /**
     * 更新自动化规则
     *
     * @param id  规则ID
     * @param dto 更新参数
     */
    void updateAutomation(Long id, BitableAutomationUpdateDTO dto);

    /**
     * 删除自动化规则（软删）
     *
     * @param id 规则ID
     */
    void deleteAutomation(Long id);

    /**
     * 切换自动化规则启用/禁用状态
     *
     * @param id      规则ID
     * @param enabled 是否启用
     */
    void toggleAutomation(Long id, boolean enabled);

    /**
     * 触发自动化检查（由记录变更事件调用）
     *
     * @param tableId       数据表ID
     * @param recordId      记录ID
     * @param changeType    变更类型: record_created/record_updated/record_deleted
     * @param changedFields 变更字段详情（可为null）
     */
    void onRecordChanged(Long tableId, Long recordId, String changeType, Map<String, Object> changedFields);

    /**
     * 执行自动化动作（由MQ消费者调用）
     *
     * @param automationId 自动化规则ID
     * @param runId        执行记录ID
     * @param context      执行上下文
     */
    void executeAutomation(Long automationId, Long runId, Map<String, Object> context);
}
