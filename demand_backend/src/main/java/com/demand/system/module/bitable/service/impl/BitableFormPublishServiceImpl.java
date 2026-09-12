package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.bitable.constant.FieldType;
import com.demand.system.module.bitable.dto.BitableFieldVO;
import com.demand.system.module.bitable.dto.BitableRecordCreateDTO;
import com.demand.system.module.bitable.dto.BitableRecordVO;
import com.demand.system.module.bitable.dto.BitableTableVO;
import com.demand.system.module.bitable.dto.CellValueDTO;
import com.demand.system.module.bitable.dto.FormPublishCreateDTO;
import com.demand.system.module.bitable.dto.FormPublishVO;
import com.demand.system.module.bitable.dto.PublicFormSchemaVO;
import com.demand.system.module.bitable.entity.BitableFormPublish;
import com.demand.system.module.bitable.mapper.BitableFormPublishMapper;
import com.demand.system.module.bitable.service.BitableAutomationService;
import com.demand.system.module.bitable.service.BitableFieldService;
import com.demand.system.module.bitable.service.BitableFormPublishService;
import com.demand.system.module.bitable.service.BitableRecordService;
import com.demand.system.module.bitable.service.BitableTableService;
import com.demand.system.module.bitable.service.BitableViewService;
import com.demand.system.module.bitable.util.BitableJsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 多维表格-公开表单发布 Service 实现。
 * 安全要点：
 * - token 为 32 字符 SecureRandom base64url；
 * - 匿名 schema 只暴露可填字段的最小配置，不暴露表/Base ID、隐藏字段、AI 提示词；
 * - 提交走 BitableRecordService 统一写路径，操作者为受限 public_form 主体（userId=0）；
 * - 密码 BCrypt 校验、有效期、提交上限在后端强制执行。
 */
@Service
public class BitableFormPublishServiceImpl implements BitableFormPublishService {

    private static final Logger log = LoggerFactory.getLogger(BitableFormPublishServiceImpl.class);

    /** 表单不可填写的字段类型（系统/计算字段） */
    private static final Set<String> NON_FILLABLE_TYPES = Set.of(
            "auto_number", "formula", "lookup", "rollup", "button",
            "created_by", "created_user", "modified_by", "modified_user",
            "created_time", "last_modified_time", "modified_time");

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final BitableFormPublishMapper publishMapper;
    private final BitableFieldService fieldService;
    private final BitableRecordService recordService;
    private final BitableTableService tableService;
    private final BitableViewService viewService;
    private final BitableAutomationService automationService;
    private final PasswordEncoder passwordEncoder;

    public BitableFormPublishServiceImpl(BitableFormPublishMapper publishMapper,
                                         BitableFieldService fieldService,
                                         BitableRecordService recordService,
                                         BitableTableService tableService,
                                         BitableViewService viewService,
                                         BitableAutomationService automationService,
                                         PasswordEncoder passwordEncoder) {
        this.publishMapper = publishMapper;
        this.fieldService = fieldService;
        this.recordService = recordService;
        this.tableService = tableService;
        this.viewService = viewService;
        this.automationService = automationService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FormPublishVO publish(Long baseId, Long tableId, Long viewId, FormPublishCreateDTO dto, Long userId) {
        // 视图必须属于当前表且为 form 类型
        var view = viewService.getViewById(viewId);
        if (view == null || !tableId.equals(view.getTableId())) {
            throw new BusinessException("视图不存在或不属于当前数据表");
        }
        if (!"form".equals(view.getViewType())) {
            throw new BusinessException("仅表单视图可以发布");
        }

        BitableFormPublish existing = selectByViewId(viewId);
        BitableFormPublish publish;
        if (existing != null) {
            publish = existing;
        } else {
            publish = new BitableFormPublish();
            publish.setViewId(viewId);
            publish.setTableId(tableId);
            publish.setToken(generateToken());
            publish.setSubmitCount(0);
            publish.setCreatedBy(userId);
        }
        publish.setStatus("enabled");
        publish.setExpireAt(dto.getExpireAt());
        publish.setSubmitLimit(dto.getSubmitLimit());
        publish.setSuccessMessage(dto.getSuccessMessage());
        publish.setRedirectUrl(dto.getRedirectUrl());
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            publish.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        }

        if (existing != null) {
            publishMapper.updateById(publish);
        } else {
            publishMapper.insert(publish);
        }
        return toVO(publish);
    }

    @Override
    public FormPublishVO getByViewId(Long viewId) {
        BitableFormPublish publish = selectByViewId(viewId);
        return publish != null ? toVO(publish) : null;
    }

    @Override
    public void updateStatus(Long viewId, boolean enabled) {
        BitableFormPublish publish = selectByViewId(viewId);
        if (publish == null) {
            throw new BusinessException("该视图尚未发布");
        }
        UpdateWrapper<BitableFormPublish> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", publish.getId()).set("status", enabled ? "enabled" : "disabled");
        publishMapper.update(null, wrapper);
    }

    @Override
    public PublicFormSchemaVO getPublicSchema(String token) {
        BitableFormPublish publish = requireAccessiblePublish(token);
        BitableTableVO table = tableService.getTableById(publish.getTableId());
        var view = viewService.getViewById(publish.getViewId());

        PublicFormSchemaVO schema = new PublicFormSchemaVO();
        schema.setTitle(table != null ? table.getName() : "表单");
        schema.setDescription(view != null && view.getName() != null && !"表单视图".equals(view.getName())
                ? view.getName() : null);
        schema.setHasPassword(publish.getPasswordHash() != null && !publish.getPasswordHash().isBlank());
        schema.setSuccessMessage(publish.getSuccessMessage());
        schema.setRedirectUrl(publish.getRedirectUrl());

        Set<Long> hiddenFieldIds = extractFormHiddenFieldIds(view != null ? view.getConfig() : null);
        List<PublicFormSchemaVO.PublicFormField> fields = new ArrayList<>();
        for (BitableFieldVO field : fieldService.listFields(publish.getTableId())) {
            if (NON_FILLABLE_TYPES.contains(field.getFieldType())) {
                continue;
            }
            if (hiddenFieldIds.contains(field.getId())) {
                continue;
            }
            Map<String, Object> fieldConfig = parseConfig(field.getConfig());
            if (Boolean.TRUE.equals(fieldConfig.get("formHidden"))) {
                continue;
            }
            PublicFormSchemaVO.PublicFormField f = new PublicFormSchemaVO.PublicFormField();
            f.setId(field.getId());
            f.setName(field.getName());
            f.setFieldType(field.getFieldType());
            f.setRequired(field.getRequired() != null && field.getRequired() == 1);
            f.setDescription(field.getDescription());
            f.setOptions(fieldConfig.get("options"));
            Object placeholder = firstNonNull(fieldConfig.get("formPlaceholder"), field.getDescription());
            f.setPlaceholder(placeholder != null ? String.valueOf(placeholder) : "请输入" + field.getName());
            fields.add(f);
            fields.add(f);
        }
        schema.setFields(fields);
        return schema;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(String token, Map<Long, Map<String, Object>> values, String password) {
        BitableFormPublish publish = requireAccessiblePublish(token);

        if (publish.getPasswordHash() != null && !publish.getPasswordHash().isBlank()) {
            if (password == null || password.isBlank()
                    || !passwordEncoder.matches(password, publish.getPasswordHash())) {
                throw new BusinessException("访问密码错误");
            }
        }
        if (publish.getSubmitLimit() != null && publish.getSubmitCount() != null
                && publish.getSubmitCount() >= publish.getSubmitLimit()) {
            throw new BusinessException("表单提交数已达上限");
        }

        Long tableId = publish.getTableId();
        Map<Long, BitableFieldVO> fillableFields = new HashMap<>();
        for (BitableFieldVO field : fieldService.listFields(tableId)) {
            if (!NON_FILLABLE_TYPES.contains(field.getFieldType())) {
                fillableFields.put(field.getId(), field);
            }
        }

        if (values == null || values.isEmpty()) {
            throw new BusinessException("表单内容不能为空");
        }

        // 校验字段合法性 + 必填
        Map<Long, CellValueDTO> cells = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<String, Object>> entry : values.entrySet()) {
            Long fieldId = entry.getKey();
            BitableFieldVO field = fillableFields.get(fieldId);
            if (field == null) {
                throw new BusinessException("字段不可填写或不存在: " + fieldId);
            }
            CellValueDTO cellValue = toCellValueDTO(entry.getValue());
            if (cellValue.getValueText() == null && cellValue.getValueNumber() == null
                    && cellValue.getValueDate() == null && cellValue.getValueJson() == null) {
                continue;
            }
            cells.put(fieldId, cellValue);
        }
        for (BitableFieldVO field : fillableFields.values()) {
            boolean required = field.getRequired() != null && field.getRequired() == 1;
            if (!required || cells.containsKey(field.getId())) {
                continue;
            }
            if (!"check".equals(field.getFieldType()) && !"checkbox".equals(field.getFieldType())) {
                throw new BusinessException("必填字段未填写: " + field.getName());
            }
        }

        BitableRecordCreateDTO dto = new BitableRecordCreateDTO();
        dto.setCells(cells);
        Long recordId = recordService.createRecord(tableId, dto, 0L);

        // 触发表单提交类自动化（失败不影响提交主流程）
        try {
            automationService.onRecordChanged(tableId, recordId, "form_submitted", null);
        } catch (Exception e) {
            log.warn("表单提交触发自动化失败: tableId={}, recordId={}", tableId, recordId, e);
        }

        // 递增提交计数
        UpdateWrapper<BitableFormPublish> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", publish.getId())
                .set("submit_count", (publish.getSubmitCount() != null ? publish.getSubmitCount() : 0) + 1);
        publishMapper.update(null, wrapper);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recordId", recordId);
        result.put("successMessage", publish.getSuccessMessage() != null ? publish.getSuccessMessage() : "提交成功");
        result.put("redirectUrl", publish.getRedirectUrl());
        return result;
    }

    // ==================== 私有辅助 ====================

    private BitableFormPublish selectByViewId(Long viewId) {
        return publishMapper.selectOne(new LambdaQueryWrapper<BitableFormPublish>()
                .eq(BitableFormPublish::getViewId, viewId));
    }

    /**
     * 校验 token 对应的发布存在且可访问；不可访问统一抛业务异常，不泄露具体原因区别
     */
    private BitableFormPublish requireAccessiblePublish(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("表单不存在或已停止收集");
        }
        BitableFormPublish publish = publishMapper.selectOne(new LambdaQueryWrapper<BitableFormPublish>()
                .eq(BitableFormPublish::getToken, token));
        if (publish == null || !"enabled".equals(publish.getStatus())) {
            throw new BusinessException("表单不存在或已停止收集");
        }
        if (publish.getExpireAt() != null && publish.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("表单已过期，停止收集");
        }
        return publish;
    }

    @SuppressWarnings("unchecked")
    private Set<Long> extractFormHiddenFieldIds(Object viewConfig) {
        Set<Long> hidden = new HashSet<>();
        if (!(viewConfig instanceof Map)) {
            return hidden;
        }
        Object form = ((Map<String, Object>) viewConfig).get("form");
        if (!(form instanceof Map)) {
            return hidden;
        }
        Object hiddenIds = ((Map<String, Object>) form).get("hiddenFieldIds");
        if (hiddenIds instanceof Collection<?> col) {
            for (Object item : col) {
                try {
                    hidden.add(Long.parseLong(String.valueOf(item)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return hidden;
    }

    private CellValueDTO toCellValueDTO(Map<String, Object> raw) {
        CellValueDTO dto = new CellValueDTO();
        if (raw == null) {
            return dto;
        }
        Object text = raw.get("valueText");
        if (text != null) {
            dto.setValueText(String.valueOf(text));
        }
        Object number = raw.get("valueNumber");
        if (number instanceof Number n) {
            dto.setValueNumber(new java.math.BigDecimal(n.toString()));
        } else if (number instanceof String s && !s.isBlank()) {
            try {
                dto.setValueNumber(new java.math.BigDecimal(s));
            } catch (NumberFormatException ignored) {
            }
        }
        Object date = raw.get("valueDate");
        if (date != null) {
            // CellValueDTO.valueDate 为 String 类型，由记录服务统一解析
            String dateStr = String.valueOf(date);
            if (!dateStr.isBlank()) {
                dto.setValueDate(dateStr);
            }
        }
        Object json = raw.get("valueJson");
        if (json != null) {
            dto.setValueJson(json);
        }
        return dto;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(Object config) {
        if (config instanceof Map) {
            return (Map<String, Object>) config;
        }
        if (config instanceof String s && !s.isBlank()) {
            Object parsed = BitableJsonUtils.parseJson(s);
            return parsed instanceof Map ? (Map<String, Object>) parsed : Map.of();
        }
        return Map.of();
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private FormPublishVO toVO(BitableFormPublish publish) {
        FormPublishVO vo = new FormPublishVO();
        vo.setId(publish.getId());
        vo.setViewId(publish.getViewId());
        vo.setTableId(publish.getTableId());
        vo.setToken(publish.getToken());
        vo.setStatus(publish.getStatus());
        vo.setHasPassword(publish.getPasswordHash() != null && !publish.getPasswordHash().isBlank());
        vo.setExpireAt(publish.getExpireAt());
        vo.setSubmitCount(publish.getSubmitCount());
        vo.setSubmitLimit(publish.getSubmitLimit());
        vo.setSuccessMessage(publish.getSuccessMessage());
        vo.setRedirectUrl(publish.getRedirectUrl());
        vo.setCreatedAt(publish.getCreatedAt());
        return vo;
    }
}
