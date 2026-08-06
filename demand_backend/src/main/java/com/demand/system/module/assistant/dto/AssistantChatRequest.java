package com.demand.system.module.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class AssistantChatRequest {

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息内容长度不能超过 4000")
    private String message;

    private AssistantPageContext pageContext;

    /**
     * 用户上传的文件附件列表。
     * <p>前端通过 /api/v1/files/upload 上传后，将文件元信息及客户端读取的文字内容传入。
     * 后端将其注入 LLM 提示词作为上下文。</p>
     */
    private List<AssistantFileAttachment> files;

    /**
     * 知识库问答范围。
     * <ul>
     *   <li>null：通用操作助手（不检索知识库，走原有操作导航流程）</li>
     *   <li>-1：全部知识库（跨所有知识库做 RAG 检索问答）</li>
     *   <li>具体正值：仅检索指定知识库</li>
     * </ul>
     */
    private Long knowledgeBaseId;

    /**
     * 指定使用的聊天模型 ID。
     * null 表示由后端按应用功能点（ASSISTANT_CHAT）自动选取默认模型。
     */
    private Long llmModelId;

    /**
     * 知识库检索模式，仅在知识库问答时生效。
     * hybrid：混合检索（语义+关键词）
     * semantic：纯语义检索
     * keyword：纯关键词检索
     */
    private String mode;

    /**
     * 知识库检索召回片段数量，仅在知识库问答时生效。
     */
    private Integer topK;

    /**
     * 是否启用联网搜索（仅在通用助手模式 knowledgeBaseId=null 下生效）。
     * <p>开启后，后端会：
     * <ol>
     *   <li>轻量检索全部本地知识库，命中相关内容则作为上下文注入</li>
     *   <li>调用 LLM 时注入厂商对应的联网搜索参数（如智谱 web_search、通义 enable_search）</li>
     *   <li>综合联网信息与本地知识库内容生成回答</li>
     * </ol>
     */
    private Boolean webSearch;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public AssistantPageContext getPageContext() {
        return pageContext;
    }

    public void setPageContext(AssistantPageContext pageContext) {
        this.pageContext = pageContext;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public Long getLlmModelId() {
        return llmModelId;
    }

    public void setLlmModelId(Long llmModelId) {
        this.llmModelId = llmModelId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Boolean getWebSearch() {
        return webSearch;
    }

    public void setWebSearch(Boolean webSearch) {
        this.webSearch = webSearch;
    }

    public List<AssistantFileAttachment> getFiles() {
        return files;
    }

    public void setFiles(List<AssistantFileAttachment> files) {
        this.files = files;
    }
}
