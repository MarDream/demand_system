package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.common.util.UserNameResolver;
import com.demand.system.module.bitable.constant.MemberRole;
import com.demand.system.module.bitable.converter.BitableConverter;
import com.demand.system.module.bitable.dto.BitableBaseMemberVO;
import com.demand.system.module.bitable.entity.BitableBaseMember;
import com.demand.system.module.bitable.mapper.BitableBaseMemberMapper;
import com.demand.system.module.bitable.service.BitableAuthorizationService;
import com.demand.system.module.bitable.service.BitableBaseMemberService;
import com.demand.system.module.bitable.util.BitableAuditHelper;
import com.demand.system.module.bitable.constant.OperationType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 多维表格-协作成员 Service 实现
 */
@Service
public class BitableBaseMemberServiceImpl implements BitableBaseMemberService {

    private final BitableBaseMemberMapper memberMapper;
    private final BitableConverter converter;
    private final UserNameResolver userNameResolver;
    private final BitableAuthorizationService authorizationService;
    private final BitableAuditHelper auditHelper;

    public BitableBaseMemberServiceImpl(BitableBaseMemberMapper memberMapper,
                                        BitableConverter converter,
                                        UserNameResolver userNameResolver,
                                        BitableAuthorizationService authorizationService,
                                        BitableAuditHelper auditHelper) {
        this.memberMapper = memberMapper;
        this.converter = converter;
        this.userNameResolver = userNameResolver;
        this.authorizationService = authorizationService;
        this.auditHelper = auditHelper;
    }

    @Override
    public List<BitableBaseMemberVO> listMembers(Long baseId) {
        List<BitableBaseMember> members = memberMapper.selectByBaseId(baseId);
        List<BitableBaseMemberVO> voList = converter.toMemberVOList(members);
        for (BitableBaseMemberVO vo : voList) {
            vo.setUserName(userNameResolver.resolveUserName(vo.getUserId(), "未知用户"));
            // avatar 暂时不填充
            vo.setAvatar(null);
        }
        return voList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addMember(Long baseId, Long userId, String role, Long operatorId) {
        MemberRole targetRole = requireValidRole(role);

        // 授予 owner 属于所有权转移，仅 Owner 本人可操作
        if (targetRole == MemberRole.OWNER) {
            requireOperatorIsOwner(baseId, operatorId);
        }

        BitableBaseMember existing = memberMapper.selectByBaseAndUser(baseId, userId);
        if (existing != null) {
            if (MemberRole.OWNER == MemberRole.fromCode(existing.getRole()) && targetRole != MemberRole.OWNER) {
                throw new BusinessException("不能修改所有者的角色");
            }
            UpdateWrapper<BitableBaseMember> wrapper = new UpdateWrapper<>();
            wrapper.eq("base_id", baseId)
                    .eq("user_id", userId)
                    .set("role", role);
            memberMapper.update(null, wrapper);
        } else {
            BitableBaseMember member = new BitableBaseMember();
            member.setBaseId(baseId);
            member.setUserId(userId);
            member.setRole(role);
            memberMapper.insert(member);
        }

        if (targetRole == MemberRole.OWNER) {
            demoteOtherOwners(baseId, userId);
        }

        // 审计
        auditHelper.record(baseId, null, operatorId, OperationType.ADD_MEMBER,
                "{\"userId\":" + userId + ",\"role\":\"" + role + "\"}");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMemberRole(Long baseId, Long userId, String role, Long operatorId) {
        MemberRole targetRole = requireValidRole(role);

        BitableBaseMember existing = memberMapper.selectByBaseAndUser(baseId, userId);
        if (existing == null) {
            throw new BusinessException("成员不存在");
        }

        // 不允许将 owner 改为其他角色（所有权转移通过把他人设为 owner 完成）
        if (MemberRole.OWNER == MemberRole.fromCode(existing.getRole()) && targetRole != MemberRole.OWNER) {
            throw new BusinessException("不能修改所有者的角色");
        }

        // 授予 owner 属于所有权转移，仅 Owner 本人可操作；ADMIN 不能把自己或他人提为 Owner
        if (targetRole == MemberRole.OWNER) {
            requireOperatorIsOwner(baseId, operatorId);
        }

        UpdateWrapper<BitableBaseMember> wrapper = new UpdateWrapper<>();
        wrapper.eq("base_id", baseId)
                .eq("user_id", userId)
                .set("role", role);
        memberMapper.update(null, wrapper);

        if (targetRole == MemberRole.OWNER) {
            demoteOtherOwners(baseId, userId);
        }

        // 审计
        auditHelper.record(baseId, null, operatorId, OperationType.UPDATE_MEMBER_ROLE,
                "{\"userId\":" + userId + ",\"role\":\"" + role + "\"}");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long baseId, Long userId, Long operatorId) {
        BitableBaseMember existing = memberMapper.selectByBaseAndUser(baseId, userId);
        if (existing == null) {
            throw new BusinessException("成员不存在");
        }

        // 不允许移除 owner
        if (MemberRole.OWNER == MemberRole.fromCode(existing.getRole())) {
            throw new BusinessException("不能移除所有者");
        }

        // 物理删除（成员表无 deleted_at）
        LambdaQueryWrapper<BitableBaseMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BitableBaseMember::getBaseId, baseId)
                .eq(BitableBaseMember::getUserId, userId);
        memberMapper.delete(wrapper);

        // 审计：operatorId 必须透传，传 null 会被 bitable_operations.user_id 的 NOT NULL 拒绝，
        // 异常又被 BitableAuditHelper 吞成 WARN，导致「移除成员」记录静默丢失
        auditHelper.record(baseId, null, operatorId, OperationType.REMOVE_MEMBER,
                "{\"userId\":" + userId + "}");
    }

    private MemberRole requireValidRole(String role) {
        MemberRole targetRole = MemberRole.fromCode(role);
        if (targetRole == null) {
            throw new BusinessException("不支持的成员角色: " + role);
        }
        return targetRole;
    }

    private void requireOperatorIsOwner(Long baseId, Long operatorId) {
        MemberRole operatorRole = authorizationService.getMemberRole(baseId, operatorId);
        if (operatorRole != MemberRole.OWNER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅所有者可以授予或转移所有者角色");
        }
    }

    /**
     * 保证 Base 内 Owner 唯一：所有权转移后，原 Owner 降级为管理员
     */
    private void demoteOtherOwners(Long baseId, Long newOwnerId) {
        UpdateWrapper<BitableBaseMember> wrapper = new UpdateWrapper<>();
        wrapper.eq("base_id", baseId)
                .eq("role", MemberRole.OWNER.getCode())
                .ne("user_id", newOwnerId)
                .set("role", MemberRole.ADMIN.getCode());
        memberMapper.update(null, wrapper);
    }
}
