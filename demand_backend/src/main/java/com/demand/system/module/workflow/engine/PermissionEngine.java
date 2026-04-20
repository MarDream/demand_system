package com.demand.system.module.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.module.user.entity.UserOrganization;
import com.demand.system.module.user.mapper.UserOrganizationMapper;
import com.demand.system.module.workflow.entity.WorkflowTransition;
import com.demand.system.module.workflow.mapper.WorkflowTransitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionEngine {

    private final WorkflowTransitionMapper transitionMapper;
    private final UserOrganizationMapper userOrganizationMapper;

    /**
     * 判断用户是否有权执行状态转换
     *
     * @param requirementId 需求ID
     * @param fromStateId   起始状态ID
     * @param toStateId     目标状态ID
     * @param userId        用户ID
     * @return true 如果允许转换
     */
    public boolean canTransition(Long requirementId, Long fromStateId, Long toStateId, Long userId) {
        // 查询匹配的转换规则
        LambdaQueryWrapper<WorkflowTransition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowTransition::getFromStateId, fromStateId)
               .eq(WorkflowTransition::getToStateId, toStateId);
        WorkflowTransition transition = transitionMapper.selectOne(wrapper);

        if (transition == null) {
            log.warn("No transition configured from state {} to state {}", fromStateId, toStateId);
            return false;
        }

        // 如果没有配置角色限制，允许所有用户
        String allowedRoles = transition.getAllowedRoles();
        if (allowedRoles == null || allowedRoles.trim().isEmpty()) {
            log.debug("No role restrictions on transition {}, allowing all users", transition.getId());
            return true;
        }

        // 查询用户的系统角色
        LambdaQueryWrapper<UserOrganization> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.eq(UserOrganization::getUserId, userId);
        List<UserOrganization> userOrgs = userOrganizationMapper.selectList(userRoleWrapper);

        // 解析允许的角色列表，按逗号分割后精确匹配
        String[] allowedRoleArray = allowedRoles.split(",");
        for (String allowedRole : allowedRoleArray) {
            String trimmedAllowedRole = allowedRole.trim();
            for (UserOrganization userOrg : userOrgs) {
                String userRole = userOrg.getSystemRole();
                if (userRole != null && userRole.trim().equals(trimmedAllowedRole)) {
                    log.debug("User {} has role {} which is allowed for transition {}", userId, userRole, transition.getId());
                    return true;
                }
            }
        }

        log.warn("User {} does not have any of the required roles {} for transition {}",
                userId, allowedRoles, transition.getId());
        return false;
    }
}
