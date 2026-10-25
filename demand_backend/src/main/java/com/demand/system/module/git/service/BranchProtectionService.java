package com.demand.system.module.git.service;

import com.demand.system.module.git.dto.BranchProtectionRuleDTO;
import com.demand.system.module.git.dto.BranchProtectionRuleVO;
import com.demand.system.module.git.dto.ProtectionRuleSetDTO;

import java.util.List;

public interface BranchProtectionService {

    List<BranchProtectionRuleVO> listRules(Long repoId);

    BranchProtectionRuleVO createRule(Long repoId, BranchProtectionRuleDTO dto);

    BranchProtectionRuleVO updateRule(Long id, BranchProtectionRuleDTO dto);

    void deleteRule(Long id);

    void toggleRule(Long id, Integer enabled);

    List<ProtectionRuleSetDTO> listRuleSets();

    ProtectionRuleSetDTO createRuleSet(ProtectionRuleSetDTO dto);

    /**
     * 将规则集应用到某仓库（生成一条该仓库的分支保护规则）
     */
    BranchProtectionRuleVO applyRuleSet(Long ruleSetId, Long repoId);
}
