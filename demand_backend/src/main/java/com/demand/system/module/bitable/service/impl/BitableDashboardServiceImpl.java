package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.common.result.PageResult;
import com.demand.system.module.bitable.dto.BitableCellValueVO;
import com.demand.system.module.bitable.dto.BitableRecordVO;
import com.demand.system.module.bitable.dto.RecordQueryDTO;
import com.demand.system.module.bitable.entity.BitableDashboard;
import com.demand.system.module.bitable.entity.BitableDashboardWidget;
import com.demand.system.module.bitable.entity.BitableTable;
import com.demand.system.module.bitable.mapper.BitableDashboardMapper;
import com.demand.system.module.bitable.mapper.BitableDashboardWidgetMapper;
import com.demand.system.module.bitable.mapper.BitableTableMapper;
import com.demand.system.module.bitable.service.BitableDashboardService;
import com.demand.system.module.bitable.service.BitableRecordService;
import com.demand.system.module.bitable.util.BitableJsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 多维表格-仪表盘 Service 实现（MVP）。
 * 数据源约束：组件只能引用与本仪表盘同 Base 的数据表字段（保存时校验），
 * 禁止任意 SQL；聚合在权限过滤后的查询结果上计算；
 * 组件上限 20 个、单图数据点上限 3000、分组上限 50。
 */
@Service
public class BitableDashboardServiceImpl implements BitableDashboardService {

    private static final Logger log = LoggerFactory.getLogger(BitableDashboardServiceImpl.class);

    private static final int MAX_WIDGETS = 20;
    private static final int MAX_DATA_POINTS = 3000;
    private static final int MAX_GROUPS = 50;
    private static final int AGGREGATION_SCAN_LIMIT = 5000;
    private static final Set<String> VALID_WIDGET_TYPES = Set.of("kpi", "bar", "line", "pie");
    private static final Set<String> VALID_AGGREGATIONS = Set.of("count", "sum", "avg", "min", "max");

    private final BitableDashboardMapper dashboardMapper;
    private final BitableDashboardWidgetMapper widgetMapper;
    private final BitableTableMapper tableMapper;
    private final BitableRecordService recordService;

    public BitableDashboardServiceImpl(BitableDashboardMapper dashboardMapper,
                                       BitableDashboardWidgetMapper widgetMapper,
                                       BitableTableMapper tableMapper,
                                       BitableRecordService recordService) {
        this.dashboardMapper = dashboardMapper;
        this.widgetMapper = widgetMapper;
        this.tableMapper = tableMapper;
        this.recordService = recordService;
    }

    @Override
    public List<BitableDashboard> listByBase(Long baseId) {
        return dashboardMapper.selectList(new LambdaQueryWrapper<BitableDashboard>()
                .eq(BitableDashboard::getBaseId, baseId)
                .orderByAsc(BitableDashboard::getId));
    }

    @Override
    public Long create(Long baseId, String name, Long userId) {
        BitableDashboard dashboard = new BitableDashboard();
        dashboard.setBaseId(baseId);
        dashboard.setName(name != null && !name.isBlank() ? name : "新建仪表盘");
        dashboard.setStatus("enabled");
        dashboard.setCreatedBy(userId);
        dashboardMapper.insert(dashboard);
        return dashboard.getId();
    }

    @Override
    public void rename(Long id, String name) {
        BitableDashboard existing = requireDashboard(id);
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<BitableDashboard> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        wrapper.eq("id", existing.getId()).set("name", name);
        dashboardMapper.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireDashboard(id);
        widgetMapper.delete(new LambdaQueryWrapper<BitableDashboardWidget>()
                .eq(BitableDashboardWidget::getDashboardId, id));
        dashboardMapper.deleteById(id);
    }

    @Override
    public List<BitableDashboardWidget> listWidgets(Long dashboardId) {
        return widgetMapper.selectList(new LambdaQueryWrapper<BitableDashboardWidget>()
                .eq(BitableDashboardWidget::getDashboardId, dashboardId)
                .orderByAsc(BitableDashboardWidget::getSortNo));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveWidgets(Long dashboardId, List<Map<String, Object>> widgets) {
        BitableDashboard dashboard = requireDashboard(dashboardId);
        int count = widgets == null ? 0 : widgets.size();
        if (count > MAX_WIDGETS) {
            throw new BusinessException("每个仪表盘最多 " + MAX_WIDGETS + " 个组件");
        }

        // 替换语义：全删全插，避免 diff 逻辑复杂化
        widgetMapper.delete(new LambdaQueryWrapper<BitableDashboardWidget>()
                .eq(BitableDashboardWidget::getDashboardId, dashboardId));

        if (widgets == null) {
            return;
        }
        int sortNo = 0;
        for (Map<String, Object> widget : widgets) {
            String type = String.valueOf(widget.getOrDefault("type", ""));
            if (!VALID_WIDGET_TYPES.contains(type)) {
                throw new BusinessException("不支持的组件类型: " + type);
            }
            Map<String, Object> dataSource = castMap(widget.get("dataSourceConfig"));
            Long tableId = toLong(dataSource.get("tableId"));
            if (tableId == null) {
                throw new BusinessException("组件数据源缺少 tableId");
            }
            // 数据源必须与本仪表盘同 Base，禁止跨 Base 取数
            BitableTable table = tableMapper.selectById(tableId);
            if (table == null || !dashboard.getBaseId().equals(table.getBaseId())) {
                throw new BusinessException("组件数据表不属于当前多维表格");
            }
            String aggregation = String.valueOf(dataSource.getOrDefault("aggregation", "count"));
            if (!VALID_AGGREGATIONS.contains(aggregation)) {
                throw new BusinessException("不支持的聚合方式: " + aggregation);
            }
            // 非计数聚合必须指定数值字段
            if (!"count".equals(aggregation) && toLong(dataSource.get("fieldId")) == null) {
                throw new BusinessException("聚合方式 " + aggregation + " 需要指定数值字段");
            }

            BitableDashboardWidget entity = new BitableDashboardWidget();
            entity.setDashboardId(dashboardId);
            entity.setType(type);
            entity.setTitle(String.valueOf(widget.getOrDefault("title", "")));
            entity.setDataSourceConfig(BitableJsonUtils.toJsonString(dataSource));
            entity.setDisplayConfig(BitableJsonUtils.toJsonString(widget.get("displayConfig")));
            entity.setSortNo(widget.containsKey("sortNo") ? toInt(widget.get("sortNo"), sortNo) : sortNo);
            widgetMapper.insert(entity);
            sortNo++;
        }
    }

    @Override
    public List<Map<String, Object>> getDashboardData(Long dashboardId) {
        BitableDashboard dashboard = requireDashboard(dashboardId);
        List<BitableDashboardWidget> widgets = listWidgets(dashboardId);

        // 每个表的数据只加载一次，多组件共享
        Map<Long, List<BitableRecordVO>> tableRecordsCache = new HashMap<>();
        List<Map<String, Object>> result = new ArrayList<>();

        for (BitableDashboardWidget widget : widgets) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("widgetId", widget.getId());
            item.put("type", widget.getType());
            item.put("title", widget.getTitle());
            try {
                item.put("data", computeWidgetData(dashboard, widget, tableRecordsCache));
            } catch (Exception e) {
                log.warn("仪表盘组件计算失败: widgetId={}", widget.getId(), e);
                item.put("data", Map.of("error", e.getMessage() != null ? e.getMessage() : "数据计算失败"));
            }
            result.add(item);
        }
        return result;
    }

    // ==================== 组件数据计算 ====================

    private Map<String, Object> computeWidgetData(BitableDashboard dashboard, BitableDashboardWidget widget,
                                                  Map<Long, List<BitableRecordVO>> cache) {
        Map<String, Object> dataSource = parseConfig(widget.getDataSourceConfig());
        Long tableId = toLong(dataSource.get("tableId"));
        Long fieldId = toLong(dataSource.get("fieldId"));
        Long groupByFieldId = toLong(dataSource.get("groupByFieldId"));
        String aggregation = String.valueOf(dataSource.getOrDefault("aggregation", "count"));

        BitableTable table = tableId != null ? tableMapper.selectById(tableId) : null;
        if (table == null || !dashboard.getBaseId().equals(table.getBaseId())) {
            throw new BusinessException("数据表不属于当前多维表格");
        }

        List<BitableRecordVO> records = cache.computeIfAbsent(tableId, k -> {
            RecordQueryDTO query = new RecordQueryDTO();
            query.setPageNum(1);
            query.setPageSize(AGGREGATION_SCAN_LIMIT);
            // 组件级筛选随查询走统一查询引擎
            Object filterConfig = dataSource.get("filterConfig");
            if (filterConfig != null) {
                query.setFilterConfig(filterConfig);
            }
            PageResult<BitableRecordVO> page = recordService.queryRecords(tableId, query);
            return page.getList();
        });

        if ("kpi".equals(widget.getType()) || groupByFieldId == null) {
            Object value = aggregate(records, aggregation, fieldId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("value", value);
            data.put("aggregation", aggregation);
            data.put("recordCount", records.size());
            return data;
        }

        // 分组聚合（bar/line/pie）
        Map<String, List<BitableRecordVO>> groups = new LinkedHashMap<>();
        for (BitableRecordVO record : records) {
            String key = extractGroupKey(record, groupByFieldId);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
        }
        List<Map.Entry<String, List<BitableRecordVO>>> sorted = new ArrayList<>(groups.entrySet());
        // 组数上限保护：超限合并为"其他"
        List<String> labels = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        int limit = Math.min(sorted.size(), MAX_GROUPS);
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, List<BitableRecordVO>> entry = sorted.get(i);
            labels.add(entry.getKey());
            values.add(aggregate(entry.getValue(), aggregation, fieldId));
        }
        if (sorted.size() > MAX_GROUPS) {
            List<BitableRecordVO> rest = new ArrayList<>();
            for (int i = MAX_GROUPS; i < sorted.size(); i++) {
                rest.addAll(sorted.get(i).getValue());
            }
            labels.add("其他");
            values.add(aggregate(rest, aggregation, fieldId));
        }
        if (labels.size() > MAX_DATA_POINTS) {
            labels = labels.subList(0, MAX_DATA_POINTS);
            values = values.subList(0, MAX_DATA_POINTS);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("labels", labels);
        data.put("values", values);
        data.put("aggregation", aggregation);
        data.put("recordCount", records.size());
        return data;
    }

    private Object aggregate(List<BitableRecordVO> records, String aggregation, Long fieldId) {
        if ("count".equals(aggregation)) {
            return records.size();
        }
        List<BigDecimal> numbers = new ArrayList<>();
        for (BitableRecordVO record : records) {
            BitableCellValueVO cell = record.getCells() != null ? record.getCells().get(fieldId) : null;
            BigDecimal num = extractNumber(cell);
            if (num != null) {
                numbers.add(num);
            }
        }
        if (numbers.isEmpty()) {
            return null;
        }
        switch (aggregation) {
            case "sum" -> {
                BigDecimal sum = BigDecimal.ZERO;
                for (BigDecimal n : numbers) {
                    sum = sum.add(n);
                }
                return sum;
            }
            case "avg" -> {
                BigDecimal total = BigDecimal.ZERO;
                for (BigDecimal n : numbers) {
                    total = total.add(n);
                }
                return total.divide(BigDecimal.valueOf(numbers.size()), 2, RoundingMode.HALF_UP);
            }
            case "min" -> {
                BigDecimal min = numbers.get(0);
                for (BigDecimal n : numbers) {
                    if (n.compareTo(min) < 0) min = n;
                }
                return min;
            }
            case "max" -> {
                BigDecimal max = numbers.get(0);
                for (BigDecimal n : numbers) {
                    if (n.compareTo(max) > 0) max = n;
                }
                return max;
            }
            default -> throw new BusinessException("不支持的聚合方式: " + aggregation);
        }
    }

    private BigDecimal extractNumber(BitableCellValueVO cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getValueNumber() != null) {
            return cell.getValueNumber();
        }
        String text = cell.getValueText();
        if (text != null && !text.isBlank()) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String extractGroupKey(BitableRecordVO record, Long fieldId) {
        BitableCellValueVO cell = record.getCells() != null ? record.getCells().get(fieldId) : null;
        if (cell == null) {
            return "(空)";
        }
        if (cell.getValueText() != null && !cell.getValueText().isBlank()) {
            return cell.getValueText();
        }
        if (cell.getValueNumber() != null) {
            return cell.getValueNumber().toPlainString();
        }
        if (cell.getValueDate() != null) {
            // 图表分组按「天」聚合：即使字段开启了「包含时间」，同一时刻序列归入同一天更符合看图直觉
            return cell.getValueDate().toLocalDate().toString();
        }
        Object json = cell.getValueJson();
        if (json instanceof Collection<?> col && !col.isEmpty()) {
            return col.stream().filter(Objects::nonNull).map(String::valueOf)
                    .reduce((a, b) -> a + ", " + b).orElse("(空)");
        }
        if (json != null) {
            return String.valueOf(json);
        }
        return "(空)";
    }

    // ==================== 辅助 ====================

    private BitableDashboard requireDashboard(Long id) {
        BitableDashboard dashboard = dashboardMapper.selectById(id);
        if (dashboard == null) {
            throw new BusinessException("仪表盘不存在");
        }
        return dashboard;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return Map.of();
        }
        Object parsed = BitableJsonUtils.parseJson(configJson);
        return parsed instanceof Map ? (Map<String, Object>) parsed : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object raw) {
        return raw instanceof Map ? (Map<String, Object>) raw : Map.of();
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        try {
            return value != null ? Long.parseLong(String.valueOf(value)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int toInt(Object value, int fallback) {
        Long l = toLong(value);
        return l != null ? l.intValue() : fallback;
    }
}
