package com.demand.system.module.workflow.support;

import com.demand.system.module.workflow.entity.WorkflowInstanceTransition;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 流转历史的展示过滤。
 *
 * <p>背景：草稿记录由 {@code WorkflowEngineService#submitFromDraft} 在提交时落库，时间取需求 created_at，
 * 用于在流转历史里展示「草稿创建」这一步。它成立的前提是草稿时间必须早于提交流转（先建草稿、后提交）。
 *
 * <p>但由运营运维平台同步/导入的工单并不存在草稿阶段，其真实流转会被回填成源平台的更早时间，
 * 于是应用侧生成的草稿记录反而成为最新一条，界面上出现「草稿 → 开始」排在「开始 → 待分析」之后的错序。
 * 这类记录不代表真实发生过的草稿阶段，应当隐藏。
 *
 * <p>真实走过草稿的工单不受影响：其草稿时间（created_at）必然早于提交时刻，会被保留。
 *
 * <p>做成无状态的静态工具（而非放到某个 Service 里）是因为流转历史有两个消费方
 * ——{@code WorkflowEngineService} 与 {@code RequirementApprovalEvaluationServiceImpl}，
 * 而后者被前者依赖，互相注入会形成循环依赖。
 */
public final class TransitionHistoryFilter {

    private TransitionHistoryFilter() {
    }

    /**
     * 剔除「时间不早于任一真实流转」的草稿记录。
     *
     * @param transitions 同一需求的流转记录（顺序不限）
     * @return 过滤后的列表；入参为 null 或不足两条时原样返回
     */
    public static List<WorkflowInstanceTransition> dropStaleDraftTransitions(
            List<WorkflowInstanceTransition> transitions) {
        if (transitions == null || transitions.size() < 2) {
            return transitions;
        }

        LocalDateTime firstRealTime = null;
        for (WorkflowInstanceTransition transition : transitions) {
            if (isDraft(transition)) {
                continue;
            }
            LocalDateTime time = effectiveTime(transition);
            if (time != null && (firstRealTime == null || time.isBefore(firstRealTime))) {
                firstRealTime = time;
            }
        }
        // 只有草稿记录、还没有任何真实流转时，保留草稿记录用于展示创建时间
        if (firstRealTime == null) {
            return transitions;
        }

        List<WorkflowInstanceTransition> kept = new ArrayList<>(transitions.size());
        for (WorkflowInstanceTransition transition : transitions) {
            if (!isDraft(transition)) {
                kept.add(transition);
                continue;
            }
            LocalDateTime time = effectiveTime(transition);
            // 仅当草稿时间严格早于最早的真实流转时才保留
            if (time != null && time.isBefore(firstRealTime)) {
                kept.add(transition);
            }
        }
        return kept;
    }

    private static boolean isDraft(WorkflowInstanceTransition transition) {
        return transition != null && "draft".equalsIgnoreCase(transition.getAction());
    }

    /** 流转时间口径：优先 started_at，缺失时回退 created_at */
    private static LocalDateTime effectiveTime(WorkflowInstanceTransition transition) {
        return transition.getStartedAt() != null ? transition.getStartedAt() : transition.getCreatedAt();
    }
}
