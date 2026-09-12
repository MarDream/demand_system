package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.dto.FormPublishCreateDTO;
import com.demand.system.module.bitable.dto.FormPublishVO;
import com.demand.system.module.bitable.dto.PublicFormSchemaVO;

import java.util.Map;

/**
 * 多维表格-公开表单发布 Service
 */
public interface BitableFormPublishService {

    /**
     * 发布表单视图（同一视图重复发布覆盖旧配置，token 不变）
     *
     * @param baseId  Base ID（用于权限校验上下文）
     * @param tableId 数据表ID
     * @param viewId  表单视图ID
     * @param dto     发布配置
     * @param userId  操作人ID
     * @return 发布信息（含 token）
     */
    FormPublishVO publish(Long baseId, Long tableId, Long viewId, FormPublishCreateDTO dto, Long userId);

    /**
     * 查询视图的发布信息（未发布返回 null）
     */
    FormPublishVO getByViewId(Long viewId);

    /**
     * 启用/停用公开表单
     */
    void updateStatus(Long viewId, boolean enabled);

    /**
     * 获取公开脱敏的表单 schema（匿名访问，凭 token）
     *
     * @param token 访问令牌
     * @return 脱敏表单结构；token 无效/停用/过期返回 null
     */
    PublicFormSchemaVO getPublicSchema(String token);

    /**
     * 匿名提交表单（凭 token）
     *
     * @param token    访问令牌
     * @param values   字段值 Map<fieldId, {valueText|valueNumber|valueDate|valueJson}>
     * @param password 访问密码（发布时设置了密码则必填）
     * @return 提交结果（成功提示语/跳转URL）
     */
    Map<String, Object> submit(String token, Map<Long, Map<String, Object>> values, String password);
}
