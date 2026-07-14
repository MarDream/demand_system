package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.util.UserNameResolver;
import com.demand.system.module.bitable.constant.MemberRole;
import com.demand.system.module.bitable.converter.BitableConverter;
import com.demand.system.module.bitable.dto.BitableBaseMemberVO;
import com.demand.system.module.bitable.entity.BitableBaseMember;
import com.demand.system.module.bitable.mapper.BitableBaseMemberMapper;
import com.demand.system.module.bitable.service.BitableBaseMemberService;
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

    public BitableBaseMemberServiceImpl(BitableBaseMemberMapper memberMapper,
                                        BitableConverter converter,
                                        UserNameResolver userNameResolver) {
        this.memberMapper = memberMapper;
        this.converter = converter;
        this.userNameResolver = userNameResolver;
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
    public void addMember(Long baseId, Long userId, String role) {
        // 验证 role 合法
        if (MemberRole.fromCode(role) == null) {
            throw new BusinessException("不支持的成员角色: " + role);
        }

        // 检查是否已存在
        BitableBaseMember existing = memberMapper.selectByBaseAndUser(baseId, userId);
        if (existing != null) {
            // 已存在则更新 role
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
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMemberRole(Long baseId, Long userId, String role) {
        // 验证 role 合法
        if (MemberRole.fromCode(role) == null) {
            throw new BusinessException("不支持的成员角色: " + role);
        }

        BitableBaseMember existing = memberMapper.selectByBaseAndUser(baseId, userId);
        if (existing == null) {
            throw new BusinessException("成员不存在");
        }

        // 不允许将 owner 改为其他角色
        if ("owner".equals(existing.getRole()) && !"owner".equals(role)) {
            throw new BusinessException("不能修改所有者的角色");
        }

        UpdateWrapper<BitableBaseMember> wrapper = new UpdateWrapper<>();
        wrapper.eq("base_id", baseId)
                .eq("user_id", userId)
                .set("role", role);
        memberMapper.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long baseId, Long userId) {
        BitableBaseMember existing = memberMapper.selectByBaseAndUser(baseId, userId);
        if (existing == null) {
            throw new BusinessException("成员不存在");
        }

        // 不允许移除 owner
        if ("owner".equals(existing.getRole())) {
            throw new BusinessException("不能移除所有者");
        }

        // 物理删除（成员表无 deleted_at）
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BitableBaseMember> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(BitableBaseMember::getBaseId, baseId)
                .eq(BitableBaseMember::getUserId, userId);
        memberMapper.delete(wrapper);
    }
}