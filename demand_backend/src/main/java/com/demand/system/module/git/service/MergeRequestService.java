package com.demand.system.module.git.service;

import com.demand.system.module.git.dto.MergeRequestDTO;
import com.demand.system.module.git.dto.MergeRequestVO;

import java.util.List;

public interface MergeRequestService {

    /**
     * 查询某仓库的合并请求列表
     */
    List<MergeRequestVO> listMergeRequests(Long repoId);

    /**
     * 创建合并请求（status = OPEN）
     */
    MergeRequestVO createMergeRequest(Long repoId, MergeRequestDTO dto);

    /**
     * 合并请求详情
     */
    MergeRequestVO getMergeRequestDetail(Long id);

    /**
     * 审批（OPEN -> APPROVED），block_self_approve 时禁止审批自己创建的 MR
     */
    MergeRequestVO approveMergeRequest(Long id);

    /**
     * 要求修改（回退到 OPEN）
     */
    MergeRequestVO requestChanges(Long id);

    /**
     * 合并（校验 APPROVED 或该仓库无需审批，APPROVED/OPEN -> MERGED）
     */
    MergeRequestVO mergeMergeRequest(Long id);
}
