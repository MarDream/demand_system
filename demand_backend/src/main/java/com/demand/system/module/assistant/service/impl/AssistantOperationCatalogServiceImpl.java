package com.demand.system.module.assistant.service.impl;

import com.demand.system.module.assistant.dto.AssistantAction;
import com.demand.system.module.assistant.dto.AssistantOperationAdvice;
import com.demand.system.module.assistant.dto.AssistantPageContext;
import com.demand.system.module.assistant.dto.AssistantSource;
import com.demand.system.module.assistant.service.AssistantOperationCatalogService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AssistantOperationCatalogServiceImpl implements AssistantOperationCatalogService {

    private static final List<CatalogItem> CATALOG = List.of(
            new CatalogItem("requirement.create", "新建需求", "/requirements/create", "button:requirement:create",
                    "创建新的需求单，填写标题、优先级、来源和描述后提交。",
                    List.of("新建需求", "创建需求", "提需求", "新增需求", "需求录入", "怎么创建需求")),
            new CatalogItem("requirement.manage", "需求管理", "/requirements", null,
                    "进入需求管理列表，筛选、查看和跟进需求状态。",
                    List.of("需求列表", "需求管理", "查看需求", "管理需求", "找需求", "需求在哪")),
            new CatalogItem("iteration.manage", "迭代管理", "/iterations", null,
                    "进入迭代管理页，查看迭代排期、容量和状态。",
                    List.of("迭代", "迭代管理", "排期", "版本计划", "sprint")),
            new CatalogItem("review.manage", "评审管理", "/reviews", null,
                    "进入评审管理页，查看需求评审、结论和待处理事项。",
                    List.of("评审", "评审管理", "review", "需求评审")),
            new CatalogItem("statistics.view", "统计报表", "/statistics", null,
                    "进入统计报表页查看需求、迭代与交付数据。",
                    List.of("统计", "报表", "数据分析", "看数据", "指标")),
            new CatalogItem("knowledge.rag", "RAG文档中心", "/settings/rag", "menu:rag",
                    "进入 RAG 文档中心，上传文档并进行智能检索。",
                    List.of("rag", "文档中心", "知识问答", "智能检索", "上传文档")),
            new CatalogItem("knowledge.manage", "知识库管理", "/settings/knowledge", "menu:rag",
                    "进入知识库管理页维护知识库、文档和搜索范围。",
                    List.of("知识库", "知识库管理", "文档库", "知识检索")),
            new CatalogItem("workflow.config", "工作流配置", "/system/workflow-config", "menu:settings:workflow",
                    "进入工作流配置页维护审批流和状态流转规则。",
                    List.of("工作流", "流程配置", "审批流", "状态流转", "节点配置")),
            new CatalogItem("llm.config", "模型配置", "/settings/llm", "menu:settings:llm",
                    "进入模型配置页维护系统接入的 LLM、Embedding 与 Rerank 模型。",
                    List.of("模型配置", "llm", "大模型", "模型管理", "ai模型")),
            new CatalogItem("project.manage", "项目管理", "/settings/projects", "menu:settings:project",
                    "进入项目管理页维护项目、归属与关联关系。",
                    List.of("项目管理", "项目配置", "项目列表", "项目在哪"))
    );

    @Override
    public AssistantOperationAdvice advise(String userMessage, AssistantPageContext pageContext, List<String> permissions, boolean superAdmin) {
        String safeMessage = userMessage == null ? "" : userMessage.trim();
        String normalizedMessage = safeMessage.toLowerCase(Locale.ROOT);
        AssistantOperationAdvice advice = new AssistantOperationAdvice();
        advice.setSessionTitle(buildSessionTitle(safeMessage, pageContext));

        List<CatalogMatch> matches = new ArrayList<>();
        for (CatalogItem item : CATALOG) {
            int score = scoreItem(item, safeMessage, normalizedMessage, pageContext);
            if (score > 0) {
                matches.add(new CatalogMatch(item, score));
            }
        }

        matches.sort(Comparator.comparingInt(CatalogMatch::score).reversed());
        if (matches.isEmpty()) {
            matches.addAll(defaultMatchesByContext(pageContext));
        }

        List<CatalogItem> matchedItems = matches.stream()
                .map(CatalogMatch::item)
                .distinct()
                .limit(4)
                .toList();

        List<CatalogItem> accessibleItems = matchedItems.stream()
                .filter(item -> canAccess(item, permissions, superAdmin))
                .limit(3)
                .toList();

        List<AssistantAction> actions = accessibleItems.stream()
                .map(this::toAction)
                .collect(Collectors.toCollection(ArrayList::new));
        advice.setActions(actions);

        List<AssistantSource> sources = new ArrayList<>();
        if (pageContext != null && pageContext.getPageTitle() != null && !pageContext.getPageTitle().isBlank()) {
            AssistantSource currentPage = new AssistantSource();
            currentPage.setCode("current.page");
            currentPage.setTitle(pageContext.getPageTitle());
            currentPage.setPath(pageContext.getRoute());
            currentPage.setReason("结合你当前所在的【" + pageContext.getPageTitle() + "】页面给出导航建议");
            sources.add(currentPage);
        }
        for (CatalogItem item : accessibleItems) {
            AssistantSource source = new AssistantSource();
            source.setCode(item.code());
            source.setTitle(item.title());
            source.setPath(item.path());
            source.setReason("系统匹配到相关入口：" + item.guidance());
            sources.add(source);
        }
        advice.setSources(deduplicateSources(sources));

        if (!accessibleItems.isEmpty()) {
            advice.setIntent(resolveIntent(accessibleItems.get(0)));
            advice.setFallbackAnswer(buildPositiveAnswer(safeMessage, pageContext, accessibleItems));
            return advice;
        }

        if (!matchedItems.isEmpty()) {
            advice.setIntent("permission_gap");
            advice.setFallbackAnswer(buildPermissionGapAnswer(matchedItems));
            return advice;
        }

        advice.setIntent("general_help");
        advice.setFallbackAnswer(buildGeneralAnswer(pageContext));
        return advice;
    }

    private String buildSessionTitle(String userMessage, AssistantPageContext pageContext) {
        if (userMessage != null && !userMessage.isBlank()) {
            String compact = userMessage.replaceAll("\s+", " ").trim();
            return compact.length() > 18 ? compact.substring(0, 18) + "…" : compact;
        }
        if (pageContext != null && pageContext.getPageTitle() != null && !pageContext.getPageTitle().isBlank()) {
            return pageContext.getPageTitle() + "助手";
        }
        return "操作助手会话";
    }

    private int scoreItem(CatalogItem item, String rawMessage, String normalizedMessage, AssistantPageContext pageContext) {
        int score = 0;
        for (String keyword : item.keywords()) {
            String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
            if ((rawMessage != null && rawMessage.contains(keyword)) || normalizedMessage.contains(normalizedKeyword)) {
                score += Math.max(20, normalizedKeyword.length() * 2);
            }
        }
        if (pageContext != null) {
            if (item.path().equals(pageContext.getActiveMenu())) {
                score += 18;
            }
            if (pageContext.getRoute() != null && pageContext.getRoute().startsWith(item.path())) {
                score += 12;
            }
            if (pageContext.getPageTitle() != null && item.title().contains(pageContext.getPageTitle())) {
                score += 6;
            }
        }
        return score;
    }

    private List<CatalogMatch> defaultMatchesByContext(AssistantPageContext pageContext) {
        if (pageContext == null) {
            return List.of(new CatalogMatch(CATALOG.get(1), 1), new CatalogMatch(CATALOG.get(2), 1), new CatalogMatch(CATALOG.get(4), 1));
        }
        String route = pageContext.getRoute() == null ? "" : pageContext.getRoute();
        if (route.startsWith("/requirements")) {
            return List.of(new CatalogMatch(CATALOG.get(0), 10), new CatalogMatch(CATALOG.get(1), 9), new CatalogMatch(CATALOG.get(2), 7));
        }
        if (route.startsWith("/iterations")) {
            return List.of(new CatalogMatch(CATALOG.get(2), 10), new CatalogMatch(CATALOG.get(1), 7), new CatalogMatch(CATALOG.get(4), 6));
        }
        if (route.startsWith("/settings/knowledge") || route.startsWith("/settings/rag")) {
            return List.of(new CatalogMatch(CATALOG.get(6), 10), new CatalogMatch(CATALOG.get(5), 9), new CatalogMatch(CATALOG.get(8), 6));
        }
        if (route.startsWith("/system/workflow-config")) {
            return List.of(new CatalogMatch(CATALOG.get(7), 10), new CatalogMatch(CATALOG.get(8), 8), new CatalogMatch(CATALOG.get(9), 6));
        }
        return List.of(new CatalogMatch(CATALOG.get(1), 3), new CatalogMatch(CATALOG.get(2), 2), new CatalogMatch(CATALOG.get(4), 2));
    }

    private boolean canAccess(CatalogItem item, List<String> permissions, boolean superAdmin) {
        if (superAdmin || item.permission() == null || item.permission().isBlank()) {
            return true;
        }
        return permissions != null && permissions.contains(item.permission());
    }

    private AssistantAction toAction(CatalogItem item) {
        AssistantAction action = new AssistantAction();
        action.setType("NAVIGATE");
        action.setLabel(item.title());
        action.setDescription(item.guidance());
        action.setTargetPath(item.path());
        action.setPermission(item.permission());
        return action;
    }

    private List<AssistantSource> deduplicateSources(List<AssistantSource> sources) {
        List<AssistantSource> result = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (AssistantSource source : sources) {
            if (source == null) {
                continue;
            }
            String key = (source.getCode() == null ? "" : source.getCode()) + "::" + (source.getPath() == null ? "" : source.getPath());
            if (keys.add(key)) {
                result.add(source);
            }
        }
        return result;
    }

    private String resolveIntent(CatalogItem item) {
        if (item.code().startsWith("knowledge.")) {
            return "knowledge_navigation";
        }
        if (item.code().startsWith("workflow.")) {
            return "workflow_navigation";
        }
        return "operation_navigation";
    }

    private String buildPositiveAnswer(String userMessage, AssistantPageContext pageContext, List<CatalogItem> items) {
        StringBuilder builder = new StringBuilder();
        if (pageContext != null && pageContext.getPageTitle() != null && !pageContext.getPageTitle().isBlank()) {
            builder.append("结合你当前所在的【").append(pageContext.getPageTitle()).append("】页面，我建议你这样操作：\n");
        } else {
            builder.append("我理解你是想确认系统中的操作入口，可以按下面方式进入：\n");
        }
        for (int i = 0; i < items.size(); i++) {
            CatalogItem item = items.get(i);
            builder.append(i + 1)
                    .append(". 进入【")
                    .append(item.title())
                    .append("】（")
                    .append(item.path())
                    .append("），")
                    .append(item.guidance())
                    .append("\n");
        }
        if (userMessage != null && !userMessage.isBlank()) {
            builder.append("如果你告诉我更具体的目标，例如“我想新建需求”或“我想配置工作流”，我还能继续拆成更细的步骤。\n");
        }
        builder.append("你也可以直接点击下面的导航按钮跳转到对应页面。");
        return builder.toString();
    }

    private String buildPermissionGapAnswer(List<CatalogItem> matchedItems) {
        CatalogItem first = matchedItems.get(0);
        return "我识别到你可能想进入【" + first.title() + "】相关功能，但当前账号看起来没有对应权限。"
                + "如果这是你需要长期使用的能力，建议联系管理员开通权限后再操作。";
    }

    private String buildGeneralAnswer(AssistantPageContext pageContext) {
        String currentPage = pageContext != null && pageContext.getPageTitle() != null && !pageContext.getPageTitle().isBlank()
                ? pageContext.getPageTitle()
                : "当前页面";
        return "我暂时还不能准确判断你的目标。你现在位于【" + currentPage + "】，可以直接告诉我你想做什么，"
                + "例如：新建需求、查看统计报表、维护知识库、配置工作流，我会给你对应的入口和操作步骤。";
    }

    private record CatalogItem(String code, String title, String path, String permission, String guidance, List<String> keywords) {
    }

    private record CatalogMatch(CatalogItem item, int score) {
    }
}
