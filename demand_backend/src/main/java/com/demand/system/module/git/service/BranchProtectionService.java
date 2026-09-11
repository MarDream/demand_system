package com.demand.system.module.git.service;

import com.demand.system.module.git.dto.BranchProtectionRuleDTO;
import com.demand.system.module.git.dto.BranchProtectionRuleVO;
import com.demand.system.module.git.dto.ProtectionRuleSetDTO;

import java.util.List;

public interface BranchProtectionService {

    /**
     * 查询某仓库的分支保护规则
     */
    List<BranchProtectionRuleVO> listRules(Long repoId);

    /**
     * 创建分支保护规则
     */
    BranchProtectionRuleVO createRule(Long repoId, BranchProtectionRuleDTO dto);

    /**
     * 更新分支保护规则
     */
    BranchProtectionRuleVO updateRule(Long id, BranchProtectionRuleDTO dto);

    /**
     * 删除分支保护规则
     */
    void deleteRule(Long id);

    /**
     * 启用/停用规则
     */
    void toggleRule(Long id, Integer enabled);

    /**
     * 规则集列表
     */
    List<ProtectionRuleSetDTO> listRuleSets();

    /**
     * 创建规则集
     */
    ProtectionRuleSetDTO createRuleSet(ProtectionRuleSetDTO dto);

    /**
     * 将规则集应用到某仓库（生成一条该仓库的分支保护规则）
     */
    BranchProtectionRuleVO applyRuleSet(Long ruleSetId, Long repoId);
}
