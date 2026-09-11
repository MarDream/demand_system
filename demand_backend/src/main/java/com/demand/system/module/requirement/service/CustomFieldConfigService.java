package com.demand.system.module.requirement.service;

import com.demand.system.common.result.Result;
import com.demand.system.module.requirement.dto.CustomFieldConfigDTO;
import com.demand.system.module.requirement.dto.SortItemDTO;
import com.demand.system.module.requirement.entity.CustomField;

import java.util.List;

/** 需求动态字段定义管理服务。 */
public interface CustomFieldConfigService {

    /** 查询某项目/需求类型下全部启用的字段定义。 */
    Result<List<CustomFieldConfigDTO>> listFields(Long projectId, String typeCode);

    /** 构建创建态字段 schema：字段定义 + 流程首节点权限 + 默认值。 */
    Result<List<CustomFieldConfigDTO>> buildCreateSchema(Long projectId, String typeCode);

    /** 创建字段。校验 fieldCode 唯一性、格式、固定字段冲突、类型配置。 */
    Result<Void> createField(CustomField field);

    /** 更新字段：允许改 name / options / required / defaultValue / sortOrder / enabled；禁止改 fieldCode。 */
    Result<Void> updateField(CustomField field);

    /** 软删除字段。存在值引用时提示，不物理删除。 */
    Result<Void> deleteField(Long id);

    /** 批量排序。 */
    Result<List<CustomField>> sortFields(List<SortItemDTO> items);
}
