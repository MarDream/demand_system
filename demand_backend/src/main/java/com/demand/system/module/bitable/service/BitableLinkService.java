package com.demand.system.module.bitable.service;

import java.util.List;

import com.demand.system.module.bitable.dto.BitableRecordVO;

/**
 * 多维表格-关联字段 Service
 */
public interface BitableLinkService {

    /**
     * 获取关联表可关联的记录列表
     *
     * @param targetTableId 目标表ID
     * @param keyword       搜索关键词
     * @param pageSize      每页大小
     * @return 可关联记录列表
     */
    List<BitableRecordVO> listLinkableRecords(Long targetTableId, String keyword, Integer pageSize);

    /**
     * 创建关联记录关系
     *
     * @param fieldId         关联字段ID
     * @param recordId        当前记录ID
     * @param targetRecordIds 目标记录ID列表
     * @param userId          操作人ID
     */
    void linkRecords(Long fieldId, Long recordId, List<Long> targetRecordIds, Long userId);

    /**
     * 获取某记录在某关联字段下的关联记录ID列表
     *
     * @param fieldId  关联字段ID
     * @param recordId 记录ID
     * @return 关联记录ID列表
     */
    List<Long> getLinkedRecordIds(Long fieldId, Long recordId);
}
