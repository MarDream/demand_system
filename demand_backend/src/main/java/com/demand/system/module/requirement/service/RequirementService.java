package com.demand.system.module.requirement.service;

import com.demand.system.common.result.PageResult;
import com.demand.system.module.requirement.dto.RequirementCreateDTO;
import com.demand.system.module.requirement.dto.RequirementApprovalEvaluationVO;
import com.demand.system.module.requirement.dto.RequirementCommentCreateDTO;
import com.demand.system.module.requirement.dto.RequirementCommentVO;
import com.demand.system.module.requirement.dto.RequirementDraftCreateDTO;
import com.demand.system.module.requirement.dto.RequirementDraftUpdateDTO;
import com.demand.system.module.requirement.dto.RequirementMyListQueryDTO;
import com.demand.system.module.requirement.dto.RequirementQueryDTO;
import com.demand.system.module.requirement.dto.RequirementSubmitDTO;
import com.demand.system.module.requirement.dto.RequirementUpdateDTO;
import com.demand.system.module.requirement.dto.RequirementListVO;
import com.demand.system.module.requirement.dto.RequirementVO;
import com.demand.system.module.requirement.dto.NextNodeOptionDTO;
import com.demand.system.module.requirement.dto.RequirementDetailVO;

import java.util.List;
import java.util.Map;

public interface RequirementService {

    PageResult<RequirementListVO> list(RequirementQueryDTO query);

    /**
     * 导出用：按检索条件查询全量需求数据
     *
     * @param query 检索条件
     * @param view  视图类型：all / drafts / pending / done / follows / cc
     * @return 导出数据列表（每行为 Map，key 为字段名）
     */
    List<Map<String, Object>> listForExport(RequirementQueryDTO query, String view);

    RequirementVO getDetail(Long id);

    void create(RequirementCreateDTO dto, Long creatorId);

    void update(RequirementUpdateDTO dto, Long userId);

    Long createDraft(RequirementDraftCreateDTO dto, Long creatorId);

    void updateDraft(RequirementDraftUpdateDTO dto, Long userId);

    List<NextNodeOptionDTO> getNextNodes(Long requirementId, Long userId);

    RequirementVO submit(Long requirementId, RequirementSubmitDTO dto, Long userId);

    PageResult<RequirementVO> listMyDrafts(RequirementMyListQueryDTO query, Long userId);

    PageResult<RequirementVO> listMyPending(RequirementMyListQueryDTO query, Long userId);

    PageResult<RequirementVO> listMyFollows(RequirementMyListQueryDTO query, Long userId);

    PageResult<RequirementVO> listMyCc(RequirementMyListQueryDTO query, Long userId);

    /**
     * 我的已办 - 查询用户参与过审批的需求
     * @param keyword 关键词搜索（可选）
     * @param userId 当前用户ID
     * @return 需求列表
     */
    PageResult<RequirementVO> listMyDone(RequirementMyListQueryDTO query, Long userId);

    void follow(Long requirementId, Long userId);

    void unfollow(Long requirementId, Long userId);

    void delete(Long id, Long userId);

    void restore(Long id, Long userId);

    List<RequirementCommentVO> getComments(Long requirementId);

    void addComment(Long requirementId, RequirementCommentCreateDTO dto, Long userId);

    List<RequirementApprovalEvaluationVO> getApprovalEvaluations(Long requirementId);

    List<Map<String, Object>> getHistory(Long requirementId);

    List<Map<String, Object>> getChildren(Long parentId);

    /**
     * 批量获取需求详情综合数据（包含历史、子需求、关联、评论、审批等）
     * @param id 需求ID
     * @return 需求详情综合数据
     */
    RequirementDetailVO getDetailBatch(Long id);
}
