package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.entity.BitableWebhookSubscription;

import java.util.List;
import java.util.Map;

/**
 * 多维表格-Webhook 订阅 Service（MVP）
 */
public interface BitableWebhookService {

    /** 列出 Base 下的订阅（脱敏：不返回 secret） */
    List<Map<String, Object>> listByBase(Long baseId);

    /**
     * 创建订阅
     *
     * @return 含 secret 明文（仅本次返回）
     */
    Map<String, Object> create(Long baseId, Long tableId, String name, List<String> eventTypes,
                               String url, Long userId);

    /** 启用/停用 */
    void updateStatus(Long subscriptionId, boolean enabled);

    /** 删除订阅 */
    void delete(Long subscriptionId);

    /**
     * 分发事件给匹配的订阅（异步推送，失败按策略重试）。
     * 由记录创建/更新/删除与表单提交路径调用，任何异常都不影响主流程。
     *
     * @param baseId    多维表格ID
     * @param tableId   数据表ID
     * @param eventType 事件类型: record_created/record_updated/record_deleted/form_submitted
     * @param payload   事件载荷（recordId、changedFields 等）
     */
    void dispatch(Long baseId, Long tableId, String eventType, Map<String, Object> payload);
}
