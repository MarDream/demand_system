package com.demand.system.module.requirement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.ErrorCode;
import com.demand.system.module.auth.security.SecurityUtils;
import com.demand.system.module.requirement.dto.RequirementTemplateSaveDTO;
import com.demand.system.module.requirement.dto.RequirementTemplateVO;
import com.demand.system.module.requirement.entity.RequirementTemplate;
import com.demand.system.module.requirement.entity.RequirementTypeConfig;
import com.demand.system.module.requirement.mapper.RequirementTemplateMapper;
import com.demand.system.module.requirement.mapper.RequirementTypeMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class RequirementTemplateService {

    private final RequirementTemplateMapper templateMapper;
    private final RequirementTypeMapper typeMapper;

    public RequirementTemplateService(RequirementTemplateMapper templateMapper, RequirementTypeMapper typeMapper) {
        this.templateMapper = templateMapper;
        this.typeMapper = typeMapper;
    }

    /**
     * 根据需求类型获取默认模板（单个）
     */
    public RequirementTemplateVO getTemplateByType(String typeCode) {
        // 优先查找默认模板
        RequirementTemplate template = templateMapper.selectOne(
            new LambdaQueryWrapper<RequirementTemplate>()
                .eq(RequirementTemplate::getRequirementTypeCode, typeCode)
                .eq(RequirementTemplate::getIsDefault, 1)
                .eq(RequirementTemplate::getIsActive, 1)
                .eq(RequirementTemplate::getDeletedAt, 0)
        );

        // 没有默认模板，取该类型下第一个启用的模板
        if (template == null) {
            template = templateMapper.selectOne(
                new LambdaQueryWrapper<RequirementTemplate>()
                    .eq(RequirementTemplate::getRequirementTypeCode, typeCode)
                    .eq(RequirementTemplate::getIsActive, 1)
                    .eq(RequirementTemplate::getDeletedAt, 0)
                    .orderByAsc(RequirementTemplate::getSortOrder)
                    .last("LIMIT 1")
            );
        }

        if (template == null) {
            return getDefaultTemplate();
        }

        return convertToVO(template);
    }

    /**
     * 根据需求类型获取所有启用的模板列表（供创建需求时选择）
     */
    public List<RequirementTemplateVO> getTemplatesByType(String typeCode) {
        List<RequirementTemplate> templates = templateMapper.selectList(
            new LambdaQueryWrapper<RequirementTemplate>()
                .eq(RequirementTemplate::getRequirementTypeCode, typeCode)
                .eq(RequirementTemplate::getIsActive, 1)
                .eq(RequirementTemplate::getDeletedAt, 0)
                .orderByAsc(RequirementTemplate::getSortOrder)
        );

        return templates.stream()
            .map(this::convertToVO)
            .collect(Collectors.toList());
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

        // 批量获取类型名称映射
        Map<String, String> typeNameMap = getTypeNameMap();

        return templates.stream()
            .map(t -> {
                RequirementTemplateVO vo = convertToVO(t);
                vo.setRequirementTypeName(typeNameMap.getOrDefault(t.getRequirementTypeCode(), t.getRequirementTypeCode()));
                return vo;
            })
            .collect(Collectors.toList());
    }

    /**
     * 保存或更新模板（支持1:N）
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
        if (isTemplateContentEmpty(dto.getTemplateContent())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模板内容不能为空");
        }

        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未登录");
        }

        if (dto.getId() != null) {
            // 更新已有模板
            RequirementTemplate existing = templateMapper.selectById(dto.getId());
            if (existing == null || existing.getDeletedAt() == 1) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
            }
            existing.setTemplateName(dto.getTemplateName());
            existing.setTemplateContent(dto.getTemplateContent());
            existing.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : existing.getIsDefault());
            existing.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : existing.getSortOrder());
            existing.setUpdatedAt(LocalDateTime.now());
            templateMapper.updateById(existing);

            // 如果设为默认，取消同类型其他模板的默认标记
            if (existing.getIsDefault() == 1) {
                clearOtherDefaults(existing.getRequirementTypeCode(), existing.getId());
            }
        } else {
            // 新建模板
            RequirementTemplate template = new RequirementTemplate();
            template.setRequirementTypeCode(dto.getRequirementTypeCode());
            template.setTemplateName(dto.getTemplateName());
            template.setTemplateContent(dto.getTemplateContent());
            template.setIsActive(1);
            template.setIsDefault(dto.getIsDefault() != null ? dto.getIsDefault() : 0);
            template.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
            template.setCreatorId(userId.intValue());
            template.setDeletedAt(0);
            templateMapper.insert(template);

            // 如果设为默认，取消同类型其他模板的默认标记
            if (template.getIsDefault() == 1) {
                clearOtherDefaults(template.getRequirementTypeCode(), template.getId());
            }
        }
    }

    /**
     * 删除模板（软删除）
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
     * 将指定模板设为该类型的默认模板
     */
    @Transactional
    public void setDefaultTemplate(Long id) {
        RequirementTemplate template = templateMapper.selectById(id);
        if (template == null || template.getDeletedAt() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        }
        if (template.getIsActive() != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "禁用的模板不能设为默认");
        }

        // 取消同类型其他模板的默认标记
        clearOtherDefaults(template.getRequirementTypeCode(), id);

        // 设为默认
        template.setIsDefault(1);
        templateMapper.updateById(template);
    }

    /**
     * 获取默认模板（系统级）
     */
    private RequirementTemplateVO getDefaultTemplate() {
        RequirementTemplateVO vo = new RequirementTemplateVO();
        vo.setRequirementTypeCode("DEFAULT");
        vo.setTemplateName("默认模板");
        vo.setTemplateContent(createDefaultTemplateContent());
        vo.setRequirementTypeName("默认");
        return vo;
    }

    /**
     * 创建默认模板内容
     */
    private Map<String, Object> createDefaultTemplateContent() {
        Map<String, Object> content = new HashMap<>();
        content.put("contentHtml", "<h3>需求描述</h3><p>请填写...</p>");
        return content;
    }

    private boolean isTemplateContentEmpty(Map<String, Object> templateContent) {
        if (templateContent == null) {
            return true;
        }

        Object html = templateContent.get("contentHtml");
        if (html instanceof String htmlText && !stripHtml(htmlText).isBlank()) {
            return false;
        }

        Object sectionsObj = templateContent.get("sections");
        if (sectionsObj instanceof List<?> sections && !sections.isEmpty()) {
            String legacyContent = buildLegacyContent(sections);
            return stripHtml(legacyContent).isBlank();
        }

        return true;
    }

    private String buildLegacyContent(List<?> sections) {
        List<String> parts = new ArrayList<>();
        for (Object sectionObj : sections) {
            if (!(sectionObj instanceof Map<?, ?> sectionMap)) {
                continue;
            }

            Object fieldType = sectionMap.get("fieldType");
            Object defaultContent = sectionMap.get("defaultContent");
            Object sectionName = sectionMap.get("sectionName");
            Object placeholder = sectionMap.get("placeholder");

            if (Objects.equals(fieldType, "richtext") && defaultContent instanceof String richText && !richText.isBlank()) {
                parts.add(richText);
                continue;
            }

            String heading = sectionName instanceof String && !((String) sectionName).isBlank()
                ? (String) sectionName
                : "未命名段落";
            String body = placeholder instanceof String && !((String) placeholder).isBlank()
                ? (String) placeholder
                : "请填写...";
            parts.add("<h3>" + heading + "</h3><p>" + body + "</p>");
        }
        return String.join("", parts);
    }

    private String stripHtml(String html) {
        return html
            .replaceAll("<[^>]+>", "")
            .replace("&nbsp;", " ")
            .trim();
    }

    /**
     * 取消同类型其他模板的默认标记
     */
    private void clearOtherDefaults(String typeCode, Long excludeId) {
        List<RequirementTemplate> others = templateMapper.selectList(
            new LambdaQueryWrapper<RequirementTemplate>()
                .eq(RequirementTemplate::getRequirementTypeCode, typeCode)
                .eq(RequirementTemplate::getIsDefault, 1)
                .eq(RequirementTemplate::getDeletedAt, 0)
                .ne(RequirementTemplate::getId, excludeId)
        );
        for (RequirementTemplate other : others) {
            other.setIsDefault(0);
            templateMapper.updateById(other);
        }
    }

    /**
     * 获取类型编码 -> 类型名称的映射
     */
    private Map<String, String> getTypeNameMap() {
        List<RequirementTypeConfig> types = typeMapper.selectList(null);
        return types.stream()
            .collect(Collectors.toMap(RequirementTypeConfig::getCode, RequirementTypeConfig::getName, (a, b) -> a));
    }

    /**
     * 转换为VO
     */
    private RequirementTemplateVO convertToVO(RequirementTemplate template) {
        RequirementTemplateVO vo = new RequirementTemplateVO();
        BeanUtils.copyProperties(template, vo);
        // 补充类型名称
        Map<String, String> typeNameMap = getTypeNameMap();
        vo.setRequirementTypeName(typeNameMap.getOrDefault(template.getRequirementTypeCode(), template.getRequirementTypeCode()));
        return vo;
    }
}
