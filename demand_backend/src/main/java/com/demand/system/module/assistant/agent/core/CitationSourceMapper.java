package com.demand.system.module.assistant.agent.core;

import com.demand.system.module.assistant.dto.AssistantSource;
import com.demand.system.module.knowledge.dto.KnowledgeSearchResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库/工单检索角标（citations）→ 消息"依据"来源（sources）的映射。
 * 从 AssistantServiceImpl 抽取，智能体链路与旧链路共用同一口径。
 */
public final class CitationSourceMapper {

    private CitationSourceMapper() {}

    public static List<AssistantSource> map(List<KnowledgeSearchResponse.CitationReference> citations, Long rawKbId) {
        if (citations == null || citations.isEmpty()) {
            return List.of();
        }
        List<AssistantSource> sources = new ArrayList<>();
        for (KnowledgeSearchResponse.CitationReference citation : citations) {
            AssistantSource source = new AssistantSource();
            boolean requirementBody = citation.getSourceType() != null
                    && citation.getSourceType().startsWith("requirement_body");
            source.setCode(requirementBody ? "requirement_body" : "knowledge_document");
            source.setSourceType(citation.getSourceType());
            source.setDocumentId(requirementBody ? null : citation.getDocumentId());
            source.setRequirementId(citation.getRequirementId());
            source.setRequirementNo(citation.getRequirementNo());
            source.setRequirementTitle(citation.getRequirementTitle());
            source.setContentType(citation.getContentType());
            source.setImageFileId(citation.getImageFileId());
            source.setImagePosition(citation.getImagePosition());
            source.setFocus(citation.getFocus());
            source.setTitle(requirementBody
                    ? ((citation.getRequirementNo() != null ? citation.getRequirementNo() + " " : "")
                    + (citation.getRequirementTitle() != null ? citation.getRequirementTitle() : "工单正文"))
                    : citation.getFileName());
            // 正文来源不进入附件预览链路；知识库附件仍携带知识库和文档标识。
            if (requirementBody) {
                source.setKnowledgeBaseId(null);
                source.setPath(citation.getRequirementId() == null ? null : "/requirements/" + citation.getRequirementId());
            } else {
                // 优先用 citation 自带的 knowledgeBaseId（跨库检索场景），否则用 rawKbId
                Long kbId = citation.getKnowledgeBaseId() != null
                        ? Long.valueOf(citation.getKnowledgeBaseId())
                        : (rawKbId != null && rawKbId != -1L ? rawKbId : null);
                source.setKnowledgeBaseId(kbId);
                source.setPath(rawKbId != null && rawKbId != -1L
                        ? "/settings/knowledge/" + rawKbId
                        : "/settings/knowledge");
            }
            int hitCount = citation.getHitCount() != null ? citation.getHitCount() : 0;
            double maxScore = citation.getMaxScore() != null ? citation.getMaxScore() : 0d;
            source.setHitCount(hitCount);
            source.setMaxScore(maxScore);
            String evidenceType = switch (citation.getContentType() == null ? "" : citation.getContentType()) {
                case "image_ocr" -> "，包含图片 OCR";
                case "image_caption" -> "，包含图片理解";
                case "body_image" -> "，包含正文图片 OCR/理解";
                default -> "";
            };
            source.setReason("命中 " + hitCount + " 个片段，相关度 " + Math.round(maxScore * 100) + "%" + evidenceType);
            sources.add(source);
        }
        return sources;
    }
}
