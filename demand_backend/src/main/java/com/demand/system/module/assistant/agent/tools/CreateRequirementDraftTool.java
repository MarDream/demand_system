package com.demand.system.module.assistant.agent.tools;

import com.demand.system.module.assistant.agent.core.AgentTool;
import com.demand.system.module.assistant.agent.core.AgentToolContext;
import com.demand.system.module.assistant.agent.core.AgentToolResult;
import com.demand.system.module.assistant.agent.core.ToolParamSchema;
import com.demand.system.module.requirement.dto.RequirementDraftCreateDTO;
import com.demand.system.module.requirement.service.RequirementService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 创建需求草稿（写操作）：执行前必须经用户在前端确认 */
@Component
public class CreateRequirementDraftTool implements AgentTool {

    private final RequirementService requirementService;

    public CreateRequirementDraftTool(RequirementService requirementService) {
        this.requirementService = requirementService;
    }

    @Override
    public String name() {
        return "create_requirement_draft";
    }

    @Override
    public String title() {
        return "创建需求草稿";
    }

    @Override
    public String description() {
        return "为当前用户创建一张需求草稿工单（仅创建草稿，不会提交审批）。"
                + "标题必填；描述应尽量完整。创建前必须先征得用户同意。";
    }

    @Override
    public boolean needsConfirmation() {
        return true;
    }

    @Override
    public Map<String, Object> schema() {
        return ToolParamSchema.object()
                .property("title", "string", "需求标题（简明扼要，不超过 100 字）")
                .property("description", "string", "需求描述（背景、目标、验收标准）")
                .property("priority", "string", "优先级：high/medium/low，默认 medium")
                .property("type", "string", "需求类型（如功能需求/优化/缺陷），不确定时省略")
                .property("projectId", "integer", "所属项目 ID（必填）。用户未指明项目时，不要调用本工具，先询问用户要归属到哪个项目")
                .required("title")
                .build();
    }

    @Override
    public String previewArgs(JsonNode args) {
        return "标题：" + args.path("title").asText("")
                + "；优先级：" + args.path("priority").asText("medium")
                + (args.path("projectId").isNumber() ? "；项目ID：" + args.path("projectId").asLong() : "");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode args) throws Exception {
        String title = args.path("title").asText("").trim();
        if (title.isBlank()) {
            return AgentToolResult.text("创建失败：标题不能为空");
        }
        RequirementDraftCreateDTO dto = new RequirementDraftCreateDTO();
        dto.setTitle(title);
        if (!args.path("description").isMissingNode() && args.path("description").isTextual()) {
            dto.setDescription(args.path("description").asText());
        }
        if (args.path("priority").isTextual()) {
            dto.setPriority(args.path("priority").asText());
        }
        if (args.path("type").isTextual()) {
            dto.setType(args.path("type").asText());
        }
        if (args.path("projectId").isIntegralNumber()) {
            dto.setProjectId(args.path("projectId").asLong());
        }
        context.log("info", "创建需求草稿：" + title);
        Long requirementId = requirementService.createDraft(dto, context.getUserId());
        return AgentToolResult.builder("已创建需求草稿（ID=" + requirementId + "，标题「" + title + "」）。"
                        + "草稿尚未提交，用户可在需求管理中继续编辑并提交审批。")
                .uiSummary("已创建草稿 #" + requirementId + "「" + title + "」")
                .build();
    }
}
