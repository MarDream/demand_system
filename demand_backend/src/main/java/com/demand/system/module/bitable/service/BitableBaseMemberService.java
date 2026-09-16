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
     * @param baseId     Base ID
     * @param userId     用户ID
     * @param role       角色
     * @param operatorId 操作者用户ID（授予 owner 角色时必须为 Owner 本人）
     */
    void addMember(Long baseId, Long userId, String role, Long operatorId);

    /**
     * 更新成员角色
     *
     * @param baseId     Base ID
     * @param userId     用户ID
     * @param role       角色
     * @param operatorId 操作者用户ID（授予 owner 角色时必须为 Owner 本人）
     */
    void updateMemberRole(Long baseId, Long userId, String role, Long operatorId);

    /**
     * 移除成员
     *
     * @param baseId     Base ID
     * @param userId     被移除的用户ID
     * @param operatorId 操作者用户ID（写入操作记录用，不能为 null ——
     *                   bitable_operations.user_id 是 NOT NULL 且无默认值，传 null 会导致审计静默丢失）
     */
    void removeMember(Long baseId, Long userId, Long operatorId);
}
