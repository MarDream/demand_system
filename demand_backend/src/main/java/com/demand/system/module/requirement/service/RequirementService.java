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
import com.demand.system.module.requirement.dto.RequirementVO;
import com.demand.system.module.requirement.dto.NextNodeOptionDTO;

import java.util.List;
import java.util.Map;

public interface RequirementService {

    PageResult<RequirementVO> list(RequirementQueryDTO query);

    RequirementVO getDetail(Long id);

    void create(RequirementCreateDTO dto, Long creatorId);

    void update(RequirementUpdateDTO dto, Long userId);

    Long createDraft(RequirementDraftCreateDTO dto, Long creatorId);

    void updateDraft(RequirementDraftUpdateDTO dto, Long userId);

    List<NextNodeOptionDTO> getNextNodes(Long requirementId, Long userId);

    RequirementVO submit(Long requirementId, RequirementSubmitDTO dto, Long userId);

    PageResult<RequirementVO> listMyDrafts(RequirementMyListQueryDTO query, Long userId);

    PageResult<RequirementVO> listMyPending(RequirementMyListQueryDTO query, Long userId);

    /**
     * 我的已办 - 查询用户参与过审批的需求
     * @param keyword 关键词搜索（可选）
     * @param userId 当前用户ID
     * @return 需求列表
     */
    List<RequirementVO> listMyDone(String keyword, Long userId);

    void delete(Long id, Long userId);

    void restore(Long id, Long userId);

    List<RequirementCommentVO> getComments(Long requirementId);

    void addComment(Long requirementId, RequirementCommentCreateDTO dto, Long userId);

    List<RequirementApprovalEvaluationVO> getApprovalEvaluations(Long requirementId);

    List<Map<String, Object>> getHistory(Long requirementId);

    List<Map<String, Object>> getChildren(Long parentId);
}
