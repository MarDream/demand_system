package com.demand.system.module.requirement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.requirement.dto.RequirementTemplateSaveDTO;
import com.demand.system.module.requirement.dto.RequirementTemplateVO;
import com.demand.system.module.requirement.entity.RequirementTemplate;
import com.demand.system.module.requirement.mapper.RequirementTemplateMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RequirementTemplateService {

    private final RequirementTemplateMapper templateMapper;

    public RequirementTemplateService(RequirementTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    /**
     * 根据需求类型获取模板
     */
    public RequirementTemplateVO getTemplateByType(String typeCode) {
        RequirementTemplate template = templateMapper.selectOne(
            new LambdaQueryWrapper<RequirementTemplate>()
                .eq(RequirementTemplate::getRequirementTypeCode, typeCode)
                .eq(RequirementTemplate::getIsActive, 1)
                .eq(RequirementTemplate::getDeletedAt, 0)
        );

        if (template == null) {
            return getDefaultTemplate();
        }

        return convertToVO(template);
    }

    /**
     * 获取所有模板列表
     */
    public List<RequirementTemplateVO> getAllTemplates() {
        List<RequirementTemplate> templates = templateMapper.selectList(
            new LambdaQueryWrapper<RequirementTemplate>()
                .eq(RequirementTemplate::getDeletedAt, 0)
                .orderByDesc(RequirementTemplate::getCreatedAt)
        );

        return templates.stream()
            .map(this::convertToVO)
            .collect(Collectors.toList());
    }

    /**
     * 保存或更新模板
     */
    @Transactional
    public void saveTemplate(RequirementTemplateSaveDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参数不能为空");
        }
        if (dto.getRequirementTypeCode() == null || dto.getRequirementTypeCode().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "需求类型不能为空");
        }
        if (dto.getTemplateName() == null || dto.getTemplateName().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模板名称不能为空");
        }
        if (dto.getTemplateContent() == null || dto.getTemplateContent().get("sections") == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模板内容不能为空");
        }

        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未登录");
        }

        RequirementTemplate existing = templateMapper.selectOne(
            new LambdaQueryWrapper<RequirementTemplate>()
                .eq(RequirementTemplate::getRequirementTypeCode, dto.getRequirementTypeCode())
                .eq(RequirementTemplate::getDeletedAt, 0)
        );

        if (existing != null) {
            existing.setTemplateName(dto.getTemplateName());
            existing.setTemplateContent(dto.getTemplateContent());
            existing.setUpdatedAt(LocalDateTime.now());
            templateMapper.updateById(existing);
        } else {
            RequirementTemplate template = new RequirementTemplate();
            template.setRequirementTypeCode(dto.getRequirementTypeCode());
            template.setTemplateName(dto.getTemplateName());
            template.setTemplateContent(dto.getTemplateContent());
            template.setIsActive(1);
            template.setCreatorId(userId.intValue());
            template.setDeletedAt(0);
            templateMapper.insert(template);
        }
    }

    /**
     * 删除模板
     */
    @Transactional
    public void deleteTemplate(Long id) {
        RequirementTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        }

        template.setDeletedAt(1);
        templateMapper.updateById(template);
    }

    /**
     * 启用/禁用模板
     */
    @Transactional
    public void toggleTemplateStatus(Long id, Integer isActive) {
        if (isActive == null || (isActive != 0 && isActive != 1)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态值只能是0或1");
        }

        RequirementTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        }

        template.setIsActive(isActive);
        templateMapper.updateById(template);
    }

    /**
     * 获取默认模板
     */
    private RequirementTemplateVO getDefaultTemplate() {
        RequirementTemplateVO vo = new RequirementTemplateVO();
        vo.setRequirementTypeCode("DEFAULT");
        vo.setTemplateName("默认模板");
        vo.setTemplateContent(createDefaultTemplateContent());
        return vo;
    }

    /**
     * 创建默认模板内容
     */
    private Map<String, Object> createDefaultTemplateContent() {
        Map<String, Object> content = new HashMap<>();
        content.put("sections", List.of(
            Map.of(
                "sectionId", "description",
                "sectionName", "需求描述",
                "fieldType", "richtext",
                "required", true,
                "placeholder", "请详细描述需求内容",
                "defaultContent", "<h3>需求描述</h3><p>请填写...</p>"
            )
        ));
        return content;
    }

    /**
     * 转换为VO
     */
    private RequirementTemplateVO convertToVO(RequirementTemplate template) {
        RequirementTemplateVO vo = new RequirementTemplateVO();
        BeanUtils.copyProperties(template, vo);
        return vo;
    }
}
