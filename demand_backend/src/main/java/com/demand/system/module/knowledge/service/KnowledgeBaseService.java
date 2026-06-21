package com.demand.system.module.knowledge.service;

import com.demand.system.common.result.PageResult;
import com.demand.system.module.knowledge.dto.KnowledgeBaseCreateDTO;
import com.demand.system.module.knowledge.dto.KnowledgeBaseMigrateDTO;
import com.demand.system.module.knowledge.dto.KnowledgeBaseUpdateDTO;
import com.demand.system.module.knowledge.dto.KnowledgeBaseVO;
import com.demand.system.module.knowledge.dto.KnowledgeMigrateResultVO;

import java.util.List;

public interface KnowledgeBaseService {

    KnowledgeBaseVO create(KnowledgeBaseCreateDTO dto, Long creatorId);

    KnowledgeBaseVO getById(Long id);

    PageResult<KnowledgeBaseVO> list(String name, int pageNum, int pageSize);

    KnowledgeBaseVO update(Long id, KnowledgeBaseUpdateDTO dto);

    void delete(Long id);

    List<KnowledgeBaseVO> listAll();

    /**
     * 将源知识库下的文档迁移到目标知识库。
     *
     * 业务流程：
     * 1. 校验源/目标存在且不同
     * 2. 更新 knowledge_documents / knowledge_chunks 的 knowledge_base_id
     * 3. 删除原 Milvus 向量（异步重新索引会重新生成）
     * 4. 调整两端的 docCount / chunkCount
     * 5. 通过 RabbitMQ 触发被迁移文档的重新索引
     *
     * @param sourceId 源知识库 ID
     * @param dto      迁移参数（目标 ID、可选文档 ID 列表、原因）
     * @return 迁移结果（迁移文档数、chunks 数）
     */
    KnowledgeMigrateResultVO migrateDocuments(Long sourceId, KnowledgeBaseMigrateDTO dto);

    /**
     * 设置为需求文件默认存储库
     *
     * @param knowledgeBaseId 知识库ID
     * @param operatorId 操作人ID
     */
    void setAsDefaultForRequirements(Long knowledgeBaseId, Long operatorId);

    /**
     * 取消需求文件默认存储库设置
     *
     * @param knowledgeBaseId 知识库ID
     * @param operatorId 操作人ID
     */
    void unsetDefaultForRequirements(Long knowledgeBaseId, Long operatorId);

    /**
     * 获取需求文件默认存储库ID（如果存在）
     *
     * @return 默认知识库ID，不存在返回null
     */
    Long getDefaultKnowledgeBaseIdForRequirements();
}
