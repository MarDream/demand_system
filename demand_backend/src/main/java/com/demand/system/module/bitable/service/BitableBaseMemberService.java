package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.dto.BitableBaseMemberVO;

import java.util.List;

/**
 * 多维表格-协作成员 Service
 */
public interface BitableBaseMemberService {

    /**
     * 列出 Base 的所有成员
     *
     * @param baseId Base ID
     * @return 成员列表
     */
    List<BitableBaseMemberVO> listMembers(Long baseId);

    /**
     * 添加成员（已存在则更新角色）
     *
     * @param baseId Base ID
     * @param userId 用户ID
     * @param role   角色
     */
    void addMember(Long baseId, Long userId, String role);

    /**
     * 更新成员角色
     *
     * @param baseId Base ID
     * @param userId 用户ID
     * @param role   角色
     */
    void updateMemberRole(Long baseId, Long userId, String role);

    /**
     * 移除成员
     *
     * @param baseId Base ID
     * @param userId 用户ID
     */
    void removeMember(Long baseId, Long userId);
}