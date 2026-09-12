package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.PageResult;
import com.demand.system.module.bitable.dto.BitableFieldVO;
import com.demand.system.module.bitable.dto.BitableRecordVO;
import com.demand.system.module.bitable.dto.RecordQueryDTO;
import com.demand.system.module.bitable.dto.ViewShareCreateDTO;
import com.demand.system.module.bitable.dto.ViewShareVO;
import com.demand.system.module.bitable.entity.BitableViewShare;
import com.demand.system.module.bitable.mapper.BitableViewShareMapper;
import com.demand.system.module.bitable.service.BitableFieldService;
import com.demand.system.module.bitable.service.BitableRecordService;
import com.demand.system.module.bitable.service.BitableViewService;
import com.demand.system.module.bitable.service.BitableViewShareService;
import com.demand.system.module.bitable.util.BitableJsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 多维表格-视图分享 Service 实现。
 * 分享视图是只读投影：数据经统一查询引擎（含视图筛选/排序）读取，
 * 字段按视图 hiddenFieldIds 裁剪，剔除 AI 提示词、公式表达式、关联目标表等内部配置，
 * 记录剔除创建人/更新人 ID 与姓名，不暴露成员信息。
 */
@Service
public class BitableViewShareServiceImpl implements BitableViewShareService {

    private static final Logger log = LoggerFactory.getLogger(BitableViewShareServiceImpl.class);

    /** 分享视图剔除的内部字段类型 */
    private static final Set<String> HIDDEN_SHARE_TYPES = Set.of(
            "formula", "lookup", "rollup", "button", "ai_text",
            "created_by", "created_user", "modified_by", "modified_user");

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final BitableViewShareMapper shareMapper;
    private final BitableViewService viewService;
    private final BitableFieldService fieldService;
    private final BitableRecordService recordService;

    public BitableViewShareServiceImpl(BitableViewShareMapper shareMapper,
                                       BitableViewService viewService,
                                       BitableFieldService fieldService,
                                       BitableRecordService recordService) {
        this.shareMapper = shareMapper;
        this.viewService = viewService;
        this.fieldService = fieldService;
        this.recordService = recordService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ViewShareVO share(Long viewId, ViewShareCreateDTO dto, Long userId) {
        var view = viewService.getViewById(viewId);
        if (view == null) {
            throw new BusinessException("视图不存在");
        }

        BitableViewShare existing = selectByViewId(viewId);
        BitableViewShare share;
        if (existing != null) {
            share = existing;
        } else {
            share = new BitableViewShare();
            share.setViewId(viewId);
            share.setTableId(view.getTableId());
            share.setToken(generateToken());
            share.setCreatedBy(userId);
        }
        share.setStatus("enabled");
        share.setExpireAt(dto.getExpireAt());
        share.setAllowDownload(Boolean.TRUE.equals(dto.getAllowDownload()) ? 1 : 0);

        if (existing != null) {
            shareMapper.updateById(share);
        } else {
            shareMapper.insert(share);
        }
        return toVO(share);
    }

    @Override
    public ViewShareVO getByViewId(Long viewId) {
        BitableViewShare share = selectByViewId(viewId);
        return share != null ? toVO(share) : null;
    }

    @Override
    public void updateStatus(Long viewId, boolean enabled) {
        BitableViewShare share = selectByViewId(viewId);
        if (share == null) {
            throw new BusinessException("该视图尚未开启分享");
        }
        UpdateWrapper<BitableViewShare> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", share.getId()).set("status", enabled ? "enabled" : "disabled");
        shareMapper.update(null, wrapper);
    }

    @Override
    public Map<String, Object> getPublicViewData(String token, Integer pageNum, Integer pageSize) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("分享链接无效或已失效");
        }
        BitableViewShare share = shareMapper.selectOne(new LambdaQueryWrapper<BitableViewShare>()
                .eq(BitableViewShare::getToken, token));
        if (share == null || !"enabled".equals(share.getStatus())) {
            throw new BusinessException("分享链接无效或已失效");
        }
        if (share.getExpireAt() != null && share.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("分享链接已过期");
        }

        var view = viewService.getViewById(share.getViewId());
        if (view == null || !share.getTableId().equals(view.getTableId())) {
            throw new BusinessException("分享链接无效或已失效");
        }

        int page = pageNum != null && pageNum > 0 ? pageNum : 1;
        int size = pageSize != null && pageSize > 0 ? Math.min(pageSize, 100) : 50;

        // 经统一查询引擎读取：应用视图筛选/排序/分组配置
        RecordQueryDTO query = new RecordQueryDTO();
        query.setViewId(share.getViewId());
        query.setPageNum(page);
        query.setPageSize(size);
        PageResult<BitableRecordVO> pageResult = recordService.queryRecords(share.getTableId(), query);

        // 字段脱敏：剔除内部类型与视图隐藏列
        Set<Long> hiddenFieldIds = extractHiddenFieldIds(view.getConfig());
        List<Map<String, Object>> fields = new ArrayList<>();
        for (BitableFieldVO field : fieldService.listFields(share.getTableId())) {
            if (HIDDEN_SHARE_TYPES.contains(field.getFieldType()) || hiddenFieldIds.contains(field.getId())) {
                continue;
            }
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("id", field.getId());
            f.put("name", field.getName());
            f.put("fieldType", field.getFieldType());
            f.put("description", field.getDescription());
            Map<String, Object> config = parseConfig(field.getConfig());
            Object options = config.get("options");
            if (options != null) {
                f.put("options", options);
            }
            fields.add(f);
        }
        Set<Long> shareableFieldIds = new HashSet<>();
        for (Map<String, Object> f : fields) {
            shareableFieldIds.add((Long) f.get("id"));
        }

        // 记录脱敏：剔除操作人信息与未授权列的单元格
        List<Map<String, Object>> records = new ArrayList<>();
        for (BitableRecordVO record : pageResult.getList()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", record.getId());
            Map<Long, Object> cells = new LinkedHashMap<>();
            if (record.getCells() != null) {
                for (Map.Entry<Long, com.demand.system.module.bitable.dto.BitableCellValueVO> entry
                        : record.getCells().entrySet()) {
                    if (shareableFieldIds.contains(entry.getKey())) {
                        cells.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            r.put("cells", cells);
            records.add(r);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewName", view.getName());
        result.put("viewType", view.getViewType());
        result.put("allowDownload", share.getAllowDownload() != null && share.getAllowDownload() == 1);
        result.put("fields", fields);
        result.put("records", records);
        result.put("total", pageResult.getTotal());
        result.put("pageNum", page);
        result.put("pageSize", size);
        return result;
    }

    // ==================== 私有辅助 ====================

    private BitableViewShare selectByViewId(Long viewId) {
        return shareMapper.selectOne(new LambdaQueryWrapper<BitableViewShare>()
                .eq(BitableViewShare::getViewId, viewId));
    }

    @SuppressWarnings("unchecked")
    private Set<Long> extractHiddenFieldIds(Object viewConfig) {
        Set<Long> hidden = new HashSet<>();
        if (viewConfig instanceof Map) {
            Object hiddenIds = ((Map<String, Object>) viewConfig).get("hiddenFieldIds");
            if (hiddenIds instanceof Collection<?> col) {
                for (Object item : col) {
                    try {
                        hidden.add(Long.parseLong(String.valueOf(item)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return hidden;
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

    private String generateToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ViewShareVO toVO(BitableViewShare share) {
        ViewShareVO vo = new ViewShareVO();
        vo.setId(share.getId());
        vo.setViewId(share.getViewId());
        vo.setTableId(share.getTableId());
        vo.setToken(share.getToken());
        vo.setStatus(share.getStatus());
        vo.setExpireAt(share.getExpireAt());
        vo.setAllowDownload(share.getAllowDownload() != null && share.getAllowDownload() == 1);
        vo.setCreatedAt(share.getCreatedAt());
        return vo;
    }
}
