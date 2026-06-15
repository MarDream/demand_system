package com.demand.system.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.knowledge.entity.KnowledgeBase;
import com.demand.system.module.knowledge.mapper.KnowledgeBaseMapper;

import java.util.Objects;

/**
 * 行级（数据级）权限校验工具类。
 * <p>
 * 用于在 Service 层校验"当前用户对资源是否拥有操作权限"。
 * Controller 层只做按钮级（菜单级）权限校验，由 {@code @PreAuthorize} 承担。
 *
 * <h3>设计原则</h3>
 * <ul>
 *     <li>超管直接放行，不做资源归属校验。</li>
 *     <li>非超管用户只能操作自己创建的资源。</li>
 *     <li>校验失败抛 {@link BusinessException}，业务码 403。</li>
 * </ul>
 *
 * @author Claude Code (Sprint 12 / PR-A 权限控制补全)
 */
public final class PermissionGuard {

    private PermissionGuard() {
    }

    /**
     * 校验"知识库创建人"权限：超管直接放行，非超管必须是创建人。
     *
     * @param mapper            知识库 Mapper
     * @param knowledgeBaseId   知识库 ID
     * @param userId            当前用户 ID
     * @param isSuperAdmin      当前用户是否为超管
     * @throws BusinessException FORBIDDEN 当非超管且非创建人时
     */
    public static void requireKnowledgeBaseOwner(KnowledgeBaseMapper mapper,
                                                 Long knowledgeBaseId,
                                                 Long userId,
                                                 boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return;
        }
        if (knowledgeBaseId == null || userId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅知识库创建者可操作");
        }
        KnowledgeBase kb = mapper.selectOne(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .select(KnowledgeBase::getCreatorId)
                        .eq(KnowledgeBase::getId, knowledgeBaseId)
                        .last("LIMIT 1")
        );
        if (kb == null || !Objects.equals(kb.getCreatorId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅知识库创建者可操作");
        }
    }

    /**
     * 校验当前用户是否已登录。
     */
    public static Long requireCurrentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        return userId;
    }
}
