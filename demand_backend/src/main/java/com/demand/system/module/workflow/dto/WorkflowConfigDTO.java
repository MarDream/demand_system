package com.demand.system.module.workflow.dto;

import java.util.List;

public class WorkflowConfigDTO {

    private List<WorkflowNodeDTO> nodes;

    private List<WorkflowEdgeDTO> edges;

    /**
     * 有值=编辑已有草稿版本；null=新建草稿版本
     */
    private Long versionId;

    /**
     * 目标版本号（前端传入或后端建议），新建时用于插入前冲突校验
     */
    private String version;

    /**
     * 目标版本名称
     */
    private String versionName;

    /**
     * 关联知识库ID，流转附件自动入库目标
     */
    private Long knowledgeBaseId;

    public List<WorkflowNodeDTO> getNodes() {
        return nodes;
    }

    public void setNodes(List<WorkflowNodeDTO> nodes) {
        this.nodes = nodes;
    }

    public List<WorkflowEdgeDTO> getEdges() {
        return edges;
    }

    public void setEdges(List<WorkflowEdgeDTO> edges) {
        this.edges = edges;
    }

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }
}
