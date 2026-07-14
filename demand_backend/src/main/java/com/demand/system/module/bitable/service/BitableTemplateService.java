package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.dto.BitableTemplateVO;

import java.util.List;

/**
 * 多维表格模板库 Service
 */
public interface BitableTemplateService {

    /**
     * 列出所有预设模板
     *
     * @return 模板列表
     */
    List<BitableTemplateVO> listTemplates();

    /**
     * 从模板创建 Base（含表、字段、示例视图和示例记录）
     *
     * @param templateCode 模板编码
     * @param userId       创建者ID
     * @return 新建 Base 的 ID
     */
    Long createBaseFromTemplate(String templateCode, Long userId);
}