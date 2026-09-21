package com.demand.system.module.bitable.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demand.system.common.exception.BusinessException;
import com.demand.system.module.bitable.dto.BitableCellValueVO;
import com.demand.system.module.bitable.dto.BitableRecordVO;
import com.demand.system.module.bitable.dto.RecordQueryDTO;
import com.demand.system.module.bitable.entity.BitableDashboard;
import com.demand.system.module.bitable.entity.BitableDashboardWidget;
import com.demand.system.module.bitable.entity.BitableBase;
import com.demand.system.module.bitable.entity.BitableField;
import com.demand.system.module.bitable.entity.BitableTable;
import com.demand.system.module.bitable.mapper.BitableDashboardMapper;
import com.demand.system.module.bitable.mapper.BitableDashboardWidgetMapper;
import com.demand.system.module.bitable.mapper.BitableBaseMapper;
import com.demand.system.module.bitable.mapper.BitableFieldMapper;
import com.demand.system.module.bitable.mapper.BitableTableMapper;
import com.demand.system.module.bitable.service.BitableBaseRoleService;
import com.demand.system.module.bitable.service.BitableDashboardService;
import com.demand.system.module.bitable.service.BitableLeafSortService;
import com.demand.system.module.bitable.service.BitableRecordService;
import com.demand.system.module.bitable.util.BitableJsonUtils;
import com.demand.system.module.llm.constant.LlmApplicationCode;
import com.demand.system.module.llm.service.LlmModelResolver;
import com.demand.system.module.knowledge.llm.LlmGateway;
import com.demand.system.module.knowledge.llm.LlmGatewayConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 多维表格-仪表盘 Service 实现（可视化大屏版）。
 * 数据源约束：组件只能引用与本仪表盘同 Base 的数据表字段（保存时校验），
 * 支持多数据源模式（dataSourceConfig.sources，逐源筛选、按字段名跨表对齐聚合）；
 * 禁止任意 SQL；聚合在权限过滤后的查询结果上计算；
 * 组件上限 50 个、单图数据点上限 3000、分组上限 50。
 * 布局模型：行式分区（组合布局），dashboard.layoutConfig 存行结构，
 * widget.layoutConfig 存 {rowId, span, height}。
 */
@Service
public class BitableDashboardServiceImpl implements BitableDashboardService {

    private static final Logger log = LoggerFactory.getLogger(BitableDashboardServiceImpl.class);

    private static final int MAX_WIDGETS = 50;
    private static final int MAX_DATA_POINTS = 3000;
    private static final int MAX_GROUPS = 50;
    private static final int AGGREGATION_SCAN_LIMIT = 5000;
    private static final int MAX_LAYOUT_JSON_LENGTH = 256 * 1024;
    /** 多数据源模式：单个组件最多绑定的数据表数 */
    private static final int MAX_SOURCES = 10;

    /** 静态组件：不绑定数据源 */
    private static final Set<String> STATIC_WIDGET_TYPES = Set.of("text", "image", "clock");
    /** 单值组件：统计数字/指标卡/进度图（kpi 为旧版指标卡） */
    private static final Set<String> SINGLE_VALUE_TYPES = Set.of("stat_number", "metric_card", "kpi", "progress");
    /** 分组图表组件 */
    private static final Set<String> GROUPED_WIDGET_TYPES = Set.of(
            "bar", "line", "hbar", "area", "pie", "donut", "combo", "radar", "funnel", "treemap", "rank");
    private static final Set<String> VALID_WIDGET_TYPES;
    static {
        Set<String> types = new HashSet<>(STATIC_WIDGET_TYPES);
        types.addAll(SINGLE_VALUE_TYPES);
        types.addAll(GROUPED_WIDGET_TYPES);
        VALID_WIDGET_TYPES = Set.copyOf(types);
    }

    private static final Set<String> VALID_AGGREGATIONS = Set.of("count", "sum", "avg", "min", "max");
    private static final Set<String> VALID_GRANULARITIES = Set.of("auto", "day", "week", "month");
    /** 不同组件允许的指标数量上限 */
    private static final Map<String, Integer> METRIC_LIMITS = Map.of("combo", 2, "radar", 4);

    private final BitableDashboardMapper dashboardMapper;
    private final BitableDashboardWidgetMapper widgetMapper;
    private final BitableBaseMapper baseMapper;
    private final BitableTableMapper tableMapper;
    private final BitableFieldMapper fieldMapper;
    private final BitableRecordService recordService;
    private final LlmGateway llmGateway;
    private final LlmModelResolver llmModelResolver;
    private final ObjectMapper objectMapper;
    private final BitableLeafSortService leafSortService;
    private final BitableBaseRoleService roleService;

    public BitableDashboardServiceImpl(BitableDashboardMapper dashboardMapper,
                                       BitableDashboardWidgetMapper widgetMapper,
                                       BitableBaseMapper baseMapper,
                                       BitableTableMapper tableMapper,
                                       BitableFieldMapper fieldMapper,
                                       BitableRecordService recordService,
                                       LlmGateway llmGateway,
                                       LlmModelResolver llmModelResolver,
                                       ObjectMapper objectMapper,
                                       BitableLeafSortService leafSortService,
                                       BitableBaseRoleService roleService) {
        this.dashboardMapper = dashboardMapper;
        this.widgetMapper = widgetMapper;
        this.baseMapper = baseMapper;
        this.tableMapper = tableMapper;
        this.fieldMapper = fieldMapper;
        this.recordService = recordService;
        this.llmGateway = llmGateway;
        this.llmModelResolver = llmModelResolver;
        this.objectMapper = objectMapper;
        this.leafSortService = leafSortService;
        this.roleService = roleService;
    }

    @Override
    public List<BitableDashboard> listByBase(Long baseId) {
        return dashboardMapper.selectList(new LambdaQueryWrapper<BitableDashboard>()
                .eq(BitableDashboard::getBaseId, baseId)
                .orderByAsc(BitableDashboard::getSortOrder)
                .orderByAsc(BitableDashboard::getId));
    }

    @Override
    public Long create(Long baseId, String name, Long userId) {
        String finalName = name != null && !name.isBlank() ? name : "新建仪表盘";
        checkDashboardNameAvailable(baseId, finalName, null);
        BitableDashboard dashboard = new BitableDashboard();
        dashboard.setBaseId(baseId);
        dashboard.setName(finalName);
        dashboard.setStatus("enabled");
        dashboard.setCreatedBy(userId);
        // 排在当前层级末尾：默认 0 会插到列表最前面（目录树按 sort_order 升序渲染）
        dashboard.setSortOrder(leafSortService.nextSortOrder(baseId));
        dashboardMapper.insert(dashboard);
        return dashboard.getId();
    }

    @Override
    public void rename(Long id, String name) {
        BitableDashboard existing = requireDashboard(id);
        if (name == null || name.isBlank()) {
            throw new BusinessException("仪表盘名称不能为空");
        }
        String trimmed = name.trim();
        if (!trimmed.equals(existing.getName())) {
            checkDashboardNameAvailable(existing.getBaseId(), trimmed, existing.getId());
        }
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<BitableDashboard> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        wrapper.eq("id", existing.getId()).set("name", trimmed);
        dashboardMapper.update(null, wrapper);
    }

    /**
     * 同名检测：仪表盘在目录树上按其 Base 的分组挂载，
     * 同一 Base 分组展示范围（含未分组）内同类型仪表盘名必须唯一
     */
    private void checkDashboardNameAvailable(Long baseId, String name, Long excludeDashboardId) {
        BitableBase base = baseMapper.selectById(baseId);
        if (base == null) {
            throw new BusinessException("多维表格不存在");
        }
        if (dashboardMapper.countSameNameInGroupScope(base.getGroupId(), name, excludeDashboardId) > 0) {
            throw new BusinessException("同级已存在同名仪表盘「" + name + "」");
        }
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
    public void saveWidgets(Long dashboardId, List<Map<String, Object>> widgets, Object layoutConfig) {
        BitableDashboard dashboard = requireDashboard(dashboardId);
        int count = widgets == null ? 0 : widgets.size();
        if (count > MAX_WIDGETS) {
            throw new BusinessException("每个仪表盘最多 " + MAX_WIDGETS + " 个组件");
        }

        // 行式布局与组件在同一事务中保存
        if (layoutConfig != null) {
            if (!(layoutConfig instanceof Map)) {
                throw new BusinessException("布局配置格式错误");
            }
            String layoutJson = BitableJsonUtils.toJsonString(layoutConfig);
            if (layoutJson.length() > MAX_LAYOUT_JSON_LENGTH) {
                throw new BusinessException("布局配置过大");
            }
            dashboard.setLayoutConfig(layoutJson);
            dashboardMapper.updateById(dashboard);
        }

        // upsert 语义：带已有 id 的更新，其余插入，缺失的删除（保持组件 id 稳定供布局引用）
        Map<Long, BitableDashboardWidget> existing = listWidgets(dashboardId).stream()
                .collect(Collectors.toMap(BitableDashboardWidget::getId, w -> w));
        Set<Long> keptIds = new HashSet<>();
        int sortNo = 0;
        if (widgets != null) {
            for (Map<String, Object> widget : widgets) {
                Long widgetId = saveWidget(dashboard, existing, widget, sortNo);
                if (widgetId != null) {
                    keptIds.add(widgetId);
                }
                sortNo++;
            }
        }
        for (Long staleId : existing.keySet()) {
            if (!keptIds.contains(staleId)) {
                widgetMapper.deleteById(staleId);
            }
        }
    }

    /** 保存单个组件，返回组件 id（新建时为新生成 id） */
    private Long saveWidget(BitableDashboard dashboard, Map<Long, BitableDashboardWidget> existing,
                            Map<String, Object> widget, int sortNo) {
        String type = String.valueOf(widget.getOrDefault("type", ""));
        if (!VALID_WIDGET_TYPES.contains(type)) {
            throw new BusinessException("不支持的组件类型: " + type);
        }
        String title = String.valueOf(widget.getOrDefault("title", ""));
        if (title.length() > 200) {
            throw new BusinessException("组件标题过长");
        }
        Map<String, Object> dataSource = castMap(widget.get("dataSourceConfig"));
        validateDataSource(dashboard, type, dataSource);
        Map<String, Object> display = castMap(widget.get("displayConfig"));
        Map<String, Object> layout = validateLayout(castMap(widget.get("layoutConfig")));

        BitableDashboardWidget entity;
        Long widgetId = toLong(widget.get("id"));
        if (widgetId != null && existing.containsKey(widgetId)) {
            entity = existing.get(widgetId);
        } else {
            entity = new BitableDashboardWidget();
            entity.setDashboardId(dashboard.getId());
        }
        entity.setType(type);
        entity.setTitle(title);
        entity.setDataSourceConfig(BitableJsonUtils.toJsonString(dataSource));
        entity.setDisplayConfig(display.isEmpty() ? null : BitableJsonUtils.toJsonString(display));
        entity.setLayoutConfig(layout.isEmpty() ? null : BitableJsonUtils.toJsonString(layout));
        entity.setSortNo(sortNo);
        if (entity.getId() == null) {
            widgetMapper.insert(entity);
        } else {
            widgetMapper.updateById(entity);
        }
        return entity.getId();
    }

    private void validateDataSource(BitableDashboard dashboard, String type, Map<String, Object> dataSource) {
        if (STATIC_WIDGET_TYPES.contains(type)) {
            return;
        }
        List<Map<String, Object>> sources = extractSources(dataSource);
        if (!sources.isEmpty()) {
            // 多数据源模式：每个数据表都必须属于本仪表盘的 Base
            if (sources.size() > MAX_SOURCES) {
                throw new BusinessException("一个组件最多绑定 " + MAX_SOURCES + " 个数据源");
            }
            for (Map<String, Object> source : sources) {
                Long sourceTableId = toLong(source.get("tableId"));
                // 允许未配置完的数据源行（编辑期过渡态），取数阶段再校验
                if (sourceTableId == null) {
                    continue;
                }
                BitableTable table = tableMapper.selectById(sourceTableId);
                if (table == null || !dashboard.getBaseId().equals(table.getBaseId())) {
                    throw new BusinessException("组件数据表不属于当前多维表格");
                }
            }
        } else {
            Long tableId = toLong(dataSource.get("tableId"));
            // 允许先落一个未配置完的组件（大屏编辑期增量保存），取数阶段再报错提示
            if (tableId == null) {
                return;
            }
            // 数据源必须与本仪表盘同 Base，禁止跨 Base 取数
            BitableTable table = tableMapper.selectById(tableId);
            if (table == null || !dashboard.getBaseId().equals(table.getBaseId())) {
                throw new BusinessException("组件数据表不属于当前多维表格");
            }
        }
        List<Map<String, Object>> metrics = extractMetrics(dataSource);
        int metricLimit = METRIC_LIMITS.getOrDefault(type, 1);
        if (metrics.size() > metricLimit) {
            throw new BusinessException("该组件最多支持 " + metricLimit + " 个指标");
        }
        for (Map<String, Object> metric : metrics) {
            String aggregation = String.valueOf(metric.getOrDefault("aggregation", "count"));
            if (!VALID_AGGREGATIONS.contains(aggregation)) {
                throw new BusinessException("不支持的聚合方式: " + aggregation);
            }
            // 多数据源模式按 fieldName 跨表对齐，fieldId 可为空
            boolean hasFieldName = metric.get("fieldName") != null && !String.valueOf(metric.get("fieldName")).isBlank();
            if (!"count".equals(aggregation) && toLong(metric.get("fieldId")) == null && !hasFieldName) {
                throw new BusinessException("聚合方式 " + aggregation + " 需要指定数值字段");
            }
        }
        Object dimension = dataSource.get("dimension");
        if (dimension instanceof Map<?, ?> dim) {
            Object granularityRaw = dim.get("granularity");
            String granularity = granularityRaw != null ? String.valueOf(granularityRaw) : "day";
            if (!VALID_GRANULARITIES.contains(granularity)) {
                throw new BusinessException("不支持的日期粒度: " + granularity);
            }
        }
    }

    private Map<String, Object> validateLayout(Map<String, Object> layout) {
        Map<String, Object> cleaned = new LinkedHashMap<>();
        if (layout.isEmpty()) {
            return cleaned;
        }
        Object rowId = layout.get("rowId");
        if (rowId != null) {
            cleaned.put("rowId", String.valueOf(rowId));
        }
        Integer span = toInt(layout.get("span"), null);
        if (span != null) {
            cleaned.put("span", Math.max(1, Math.min(12, span)));
        }
        Integer height = toInt(layout.get("height"), null);
        if (height != null) {
            cleaned.put("height", Math.max(80, Math.min(2000, height)));
        }
        return cleaned;
    }

    @Override
    public List<Map<String, Object>> getDashboardData(Long dashboardId) {
        return getDashboardData(dashboardId, null);
    }

    @Override
    public List<Map<String, Object>> getDashboardData(Long dashboardId, Long userId) {
        BitableDashboard dashboard = requireDashboard(dashboardId);
        List<BitableDashboardWidget> widgets = listWidgets(dashboardId);

        // 仪表盘数据权限：none=有不可查看数据时图表不可见 / view=跟随访问者权限统计 / full=全部数据（默认）
        String mode = "full";
        if (userId != null) {
            mode = roleService.resolveDashboardDataPermission(dashboard.getBaseId(), dashboardId, userId);
        }

        // 访问者无读取权限的数据表（表级 data 权限 none；未配置视为 full）。
        // 当前权限模型无记录级规则：表可读即整表可见，「存在不可查看记录」的判定落在表不可见上。
        Set<Long> restrictedTables = Collections.emptySet();
        if (userId != null && !"full".equals(mode)) {
            restrictedTables = resolveRestrictedTables(dashboard.getBaseId(), widgets, userId);
        }

        // 记录缓存键 = tableId + 筛选配置：同表不同筛选的组件不会互相污染
        Map<String, List<BitableRecordVO>> tableRecordsCache = new HashMap<>();
        Map<Long, String> fieldNameCache = new HashMap<>();
        List<Map<String, Object>> result = new ArrayList<>();

        for (BitableDashboardWidget widget : widgets) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("widgetId", widget.getId());
            item.put("type", widget.getType());
            item.put("title", widget.getTitle());
            if (!restrictedTables.isEmpty()) {
                Set<Long> sourceTables = extractWidgetTableIds(widget);
                boolean viewerBlocked = !Collections.disjoint(sourceTables, restrictedTables);
                if (viewerBlocked && "none".equals(mode)) {
                    // 有不可查看数据时，图表不可见：前端渲染「无权限」占位而非图表
                    item.put("hidden", true);
                    item.put("data", Map.of("hidden", true));
                    result.add(item);
                    continue;
                }
                // view 模式下受限表的记录置空（图表可见但统计范围为空），交给下方计算链
                Set<Long> widgetRestricted = viewerBlocked ? sourceTables : Collections.emptySet();
                try {
                    item.put("data", computeWidgetData(dashboard, widget, tableRecordsCache, fieldNameCache, widgetRestricted));
                } catch (Exception e) {
                    log.warn("仪表盘组件计算失败: widgetId={}", widget.getId(), e);
                    item.put("data", Map.of("error", e.getMessage() != null ? e.getMessage() : "数据计算失败"));
                }
                result.add(item);
                continue;
            }
            try {
                item.put("data", computeWidgetData(dashboard, widget, tableRecordsCache, fieldNameCache, Collections.emptySet()));
            } catch (Exception e) {
                log.warn("仪表盘组件计算失败: widgetId={}", widget.getId(), e);
                item.put("data", Map.of("error", e.getMessage() != null ? e.getMessage() : "数据计算失败"));
            }
            result.add(item);
        }
        return result;
    }

    /** 访问者表级 data 权限为 none 的数据表集合（仅收集组件实际引用到的表） */
    private Set<Long> resolveRestrictedTables(Long baseId, List<BitableDashboardWidget> widgets, Long userId) {
        Set<Long> referenced = new LinkedHashSet<>();
        for (BitableDashboardWidget widget : widgets) {
            referenced.addAll(extractWidgetTableIds(widget));
        }
        if (referenced.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> restricted = new LinkedHashSet<>();
        for (Long tableId : referenced) {
            String level = roleService.resolveObjectPermission(baseId, tableId, "data", userId);
            if ("none".equals(level)) {
                restricted.add(tableId);
            }
        }
        return restricted;
    }

    /** 组件引用的数据表：单数据源 tableId + 多数据源 sources[].tableId */
    private Set<Long> extractWidgetTableIds(BitableDashboardWidget widget) {
        Set<Long> tableIds = new LinkedHashSet<>();
        Map<String, Object> dataSource = parseConfig(widget.getDataSourceConfig());
        Long single = toLong(dataSource.get("tableId"));
        if (single != null) {
            tableIds.add(single);
        }
        for (Map<String, Object> source : extractSources(dataSource)) {
            Long id = toLong(source.get("tableId"));
            if (id != null) {
                tableIds.add(id);
            }
        }
        return tableIds;
    }

    // ==================== 组件数据计算 ====================

    private Map<String, Object> computeWidgetData(BitableDashboard dashboard, BitableDashboardWidget widget,
                                                  Map<String, List<BitableRecordVO>> cache,
                                                  Map<Long, String> fieldNameCache,
                                                  Set<Long> restrictedTables) {
        Map<String, Object> dataSource = parseConfig(widget.getDataSourceConfig());
        String type = widget.getType();
        if (STATIC_WIDGET_TYPES.contains(type)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("static", true);
            return data;
        }

        List<Map<String, Object>> sources = extractSources(dataSource);
        if (!sources.isEmpty()) {
            return computeMultiSourceData(dashboard, dataSource, sources, type, cache, restrictedTables);
        }

        Long tableId = toLong(dataSource.get("tableId"));
        if (tableId == null) {
            throw new BusinessException("请在属性面板中配置数据源");
        }
        BitableTable table = tableMapper.selectById(tableId);
        if (table == null || !dashboard.getBaseId().equals(table.getBaseId())) {
            throw new BusinessException("数据表不属于当前多维表格");
        }

        List<BitableRecordVO> records = loadRecords(cache, tableId, dataSource.get("filterConfig"), restrictedTables);

        List<Metric> metrics = resolveMetrics(dataSource, fieldNameCache);
        Metric primary = metrics.get(0);

        if (SINGLE_VALUE_TYPES.contains(type)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("value", aggregate(records, primary));
            data.put("aggregation", primary.aggregation);
            data.put("recordCount", records.size());
            return data;
        }

        // 分组聚合（bar/line/pie/…/rank）
        Map<String, Object> dimension = castMap(dataSource.get("dimension"));
        Long dimFieldId = dimension.containsKey("fieldId")
                ? toLong(dimension.get("fieldId"))
                : toLong(dataSource.get("groupByFieldId"));
        String granularity = String.valueOf(dimension.getOrDefault("granularity", "day"));

        Map<String, List<BitableRecordVO>> groups = new LinkedHashMap<>();
        if (dimFieldId == null) {
            groups.put("总计", records);
        } else {
            for (BitableRecordVO record : records) {
                for (String key : extractGroupKeys(record, dimFieldId, granularity)) {
                    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
                }
            }
        }

        // 排序：rank 默认按首指标降序；其余默认保持原序（时间序列天然按记录顺序）
        String sort = String.valueOf(dataSource.getOrDefault("sort", "rank".equals(type) ? "desc" : "default"));
        List<Map.Entry<String, List<BitableRecordVO>>> entries = new ArrayList<>(groups.entrySet());
        if ("asc".equalsIgnoreCase(sort) || "desc".equalsIgnoreCase(sort)) {
            Comparator<Map.Entry<String, List<BitableRecordVO>>> byValue = Comparator.comparing(e -> {
                Object v = aggregate(e.getValue(), primary);
                return v instanceof Number n ? n.doubleValue() : Double.NaN;
            });
            entries.sort("desc".equalsIgnoreCase(sort) ? byValue.reversed() : byValue);
        }

        int limit = toInt(dataSource.get("limit"), "rank".equals(type) ? 10 : MAX_GROUPS);
        limit = Math.max(1, Math.min(MAX_GROUPS, limit));
        List<String> labels = new ArrayList<>();
        List<List<Object>> seriesValues = new ArrayList<>(metrics.size());
        for (int i = 0; i < metrics.size(); i++) {
            seriesValues.add(new ArrayList<>());
        }
        for (int i = 0; i < Math.min(entries.size(), limit); i++) {
            Map.Entry<String, List<BitableRecordVO>> entry = entries.get(i);
            labels.add(entry.getKey());
            for (int m = 0; m < metrics.size(); m++) {
                seriesValues.get(m).add(aggregate(entry.getValue(), metrics.get(m)));
            }
        }
        if (entries.size() > limit) {
            List<BitableRecordVO> rest = new ArrayList<>();
            for (int i = limit; i < entries.size(); i++) {
                rest.addAll(entries.get(i).getValue());
            }
            labels.add("其他");
            for (int m = 0; m < metrics.size(); m++) {
                seriesValues.get(m).add(aggregate(rest, metrics.get(m)));
            }
        }
        if (labels.size() > MAX_DATA_POINTS) {
            labels = labels.subList(0, MAX_DATA_POINTS);
            for (int m = 0; m < seriesValues.size(); m++) {
                seriesValues.set(m, seriesValues.get(m).subList(0, MAX_DATA_POINTS));
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("labels", labels);
        List<Map<String, Object>> series = new ArrayList<>();
        for (int m = 0; m < metrics.size(); m++) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("name", metrics.get(m).name);
            s.put("aggregation", metrics.get(m).aggregation);
            s.put("data", seriesValues.get(m));
            series.add(s);
        }
        data.put("series", series);
        // 兼容旧版前端：values = 首个指标的数据
        data.put("values", seriesValues.get(0));
        data.put("aggregation", primary.aggregation);
        data.put("recordCount", records.size());
        return data;
    }

    // ==================== 多数据源聚合 ====================

    /** 单源单组单指标的部分聚合结果：跨源合并的最小单元 */
    private record AggPart(long recordCount, BigDecimal sum, long numericCount, BigDecimal min, BigDecimal max) {
    }

    /**
     * 多数据源取数：逐源加载记录（各源自带筛选，走统一查询引擎），
     * 维度/指标按字段名对齐到各表自己的 fieldId，逐源做部分聚合后跨源合并，
     * 再统一走排序 / limit /「其他」归并。输出结构与单源完全一致。
     */
    private Map<String, Object> computeMultiSourceData(BitableDashboard dashboard, Map<String, Object> dataSource,
                                                       List<Map<String, Object>> sources, String type,
                                                       Map<String, List<BitableRecordVO>> cache,
                                                       Set<Long> restrictedTables) {
        Map<String, Object> dimension = castMap(dataSource.get("dimension"));
        String dimFieldName = dimension.get("fieldName") != null && !String.valueOf(dimension.get("fieldName")).isBlank()
                ? String.valueOf(dimension.get("fieldName")) : null;
        Long legacyDimFieldId = dimension.containsKey("fieldId")
                ? toLong(dimension.get("fieldId"))
                : toLong(dataSource.get("groupByFieldId"));
        String granularity = String.valueOf(dimension.getOrDefault("granularity", "day"));
        boolean hasDimension = dimFieldName != null || legacyDimFieldId != null;

        List<Metric> metrics = resolveMultiSourceMetrics(dataSource);
        Metric primary = metrics.get(0);
        boolean singleValue = SINGLE_VALUE_TYPES.contains(type);

        // 每表字段名 → fieldId 索引，同一请求内只构建一次
        Map<Long, Map<String, Long>> tableFieldIndex = new HashMap<>();

        // 单值组件：不分维度，全量记录按指标累积部分聚合
        List<List<AggPart>> singleParts = singleValue ? new ArrayList<>() : null;
        if (singleValue) {
            for (int i = 0; i < metrics.size(); i++) {
                singleParts.add(new ArrayList<>());
            }
        }
        // 分组组件：groupKey -> 每个指标的部分聚合列表（跨源累积）
        Map<String, List<List<AggPart>>> mergedGroups = new LinkedHashMap<>();
        long recordCount = 0;
        boolean anySourceConfigured = false;

        for (Map<String, Object> source : sources) {
            Long sourceTableId = toLong(source.get("tableId"));
            if (sourceTableId == null) {
                continue;
            }
            anySourceConfigured = true;
            BitableTable table = tableMapper.selectById(sourceTableId);
            if (table == null || !dashboard.getBaseId().equals(table.getBaseId())) {
                throw new BusinessException("数据表不属于当前多维表格");
            }
            Map<String, Long> fieldIndex = tableFieldIndex.computeIfAbsent(sourceTableId, id -> {
                Map<String, Long> m = new HashMap<>();
                for (BitableField field : fieldMapper.selectList(new LambdaQueryWrapper<BitableField>()
                        .eq(BitableField::getTableId, id))) {
                    if (field.getName() != null) {
                        m.put(field.getName(), field.getId());
                    }
                }
                return m;
            });

            // 维度/指标解析到本表 fieldId；维度字段在本表不存在时记录全部计入「(空)」
            Long dimFieldId = dimFieldName != null
                    ? fieldIndex.get(dimFieldName)
                    : (legacyDimFieldId != null && fieldBelongsToTable(legacyDimFieldId, sourceTableId) ? legacyDimFieldId : null);
            Long[] metricFieldIds = new Long[metrics.size()];
            for (int m = 0; m < metrics.size(); m++) {
                Metric metric = metrics.get(m);
                if (metric.fieldName() != null) {
                    metricFieldIds[m] = fieldIndex.get(metric.fieldName());
                } else if (metric.fieldId() != null && fieldBelongsToTable(metric.fieldId(), sourceTableId)) {
                    metricFieldIds[m] = metric.fieldId();
                }
            }

            List<BitableRecordVO> records = loadRecords(cache, sourceTableId, source.get("filterConfig"), restrictedTables);
            recordCount += records.size();

            if (singleValue) {
                for (int m = 0; m < metrics.size(); m++) {
                    singleParts.get(m).add(computePart(records, metricFieldIds[m], metrics.get(m).aggregation()));
                }
                continue;
            }

            Map<String, List<BitableRecordVO>> groups = new LinkedHashMap<>();
            if (!hasDimension || dimFieldId == null) {
                groups.put(hasDimension ? "(空)" : "总计", records);
            } else {
                for (BitableRecordVO record : records) {
                    for (String key : extractGroupKeys(record, dimFieldId, granularity)) {
                        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
                    }
                }
            }
            for (Map.Entry<String, List<BitableRecordVO>> entry : groups.entrySet()) {
                List<List<AggPart>> perMetricParts = mergedGroups.computeIfAbsent(entry.getKey(), k -> {
                    List<List<AggPart>> l = new ArrayList<>();
                    for (int i = 0; i < metrics.size(); i++) {
                        l.add(new ArrayList<>());
                    }
                    return l;
                });
                for (int m = 0; m < metrics.size(); m++) {
                    perMetricParts.get(m).add(computePart(entry.getValue(), metricFieldIds[m], metrics.get(m).aggregation()));
                }
            }
        }
        if (!anySourceConfigured) {
            throw new BusinessException("请在属性面板中配置数据源");
        }

        if (singleValue) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("value", mergeValue(primary.aggregation(), singleParts.get(0)));
            data.put("aggregation", primary.aggregation());
            data.put("recordCount", recordCount);
            if (sources.size() > 1) {
                data.put("multiSource", true);
            }
            return data;
        }

        // 排序：rank 默认按首指标降序；其余默认保持原序
        String sort = String.valueOf(dataSource.getOrDefault("sort", "rank".equals(type) ? "desc" : "default"));
        List<Map.Entry<String, List<List<AggPart>>>> entries = new ArrayList<>(mergedGroups.entrySet());
        if ("asc".equalsIgnoreCase(sort) || "desc".equalsIgnoreCase(sort)) {
            Comparator<Map.Entry<String, List<List<AggPart>>>> byValue = Comparator.comparing(e -> {
                Object v = mergeValue(primary.aggregation(), e.getValue().get(0));
                return v instanceof Number n ? n.doubleValue() : Double.NaN;
            });
            entries.sort("desc".equalsIgnoreCase(sort) ? byValue.reversed() : byValue);
        }

        int limit = toInt(dataSource.get("limit"), "rank".equals(type) ? 10 : MAX_GROUPS);
        limit = Math.max(1, Math.min(MAX_GROUPS, limit));
        List<String> labels = new ArrayList<>();
        List<List<Object>> seriesValues = new ArrayList<>(metrics.size());
        for (int i = 0; i < metrics.size(); i++) {
            seriesValues.add(new ArrayList<>());
        }
        for (int i = 0; i < Math.min(entries.size(), limit); i++) {
            Map.Entry<String, List<List<AggPart>>> entry = entries.get(i);
            labels.add(entry.getKey());
            for (int m = 0; m < metrics.size(); m++) {
                seriesValues.get(m).add(mergeValue(metrics.get(m).aggregation(), entry.getValue().get(m)));
            }
        }
        if (entries.size() > limit) {
            List<List<AggPart>> restParts = new ArrayList<>();
            for (int m = 0; m < metrics.size(); m++) {
                restParts.add(new ArrayList<>());
            }
            for (int i = limit; i < entries.size(); i++) {
                for (int m = 0; m < metrics.size(); m++) {
                    restParts.get(m).addAll(entries.get(i).getValue().get(m));
                }
            }
            labels.add("其他");
            for (int m = 0; m < metrics.size(); m++) {
                seriesValues.get(m).add(mergeValue(metrics.get(m).aggregation(), restParts.get(m)));
            }
        }
        if (labels.size() > MAX_DATA_POINTS) {
            labels = labels.subList(0, MAX_DATA_POINTS);
            for (int m = 0; m < seriesValues.size(); m++) {
                seriesValues.set(m, seriesValues.get(m).subList(0, MAX_DATA_POINTS));
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("labels", labels);
        List<Map<String, Object>> series = new ArrayList<>();
        for (int m = 0; m < metrics.size(); m++) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("name", metrics.get(m).name);
            s.put("aggregation", metrics.get(m).aggregation);
            s.put("data", seriesValues.get(m));
            series.add(s);
        }
        data.put("series", series);
        // 兼容旧版前端：values = 首个指标的数据
        data.put("values", seriesValues.get(0));
        data.put("aggregation", primary.aggregation);
        data.put("recordCount", recordCount);
        if (sources.size() > 1) {
            data.put("multiSource", true);
        }
        return data;
    }

    /** 对一组记录按指定字段计算部分聚合（fieldId 为 null 时仅统计记录数） */
    private AggPart computePart(List<BitableRecordVO> records, Long fieldId, String aggregation) {
        if ("count".equals(aggregation)) {
            return new AggPart(records.size(), null, 0, null, null);
        }
        BigDecimal sum = BigDecimal.ZERO;
        long n = 0;
        BigDecimal min = null;
        BigDecimal max = null;
        for (BitableRecordVO record : records) {
            BitableCellValueVO cell = record.getCells() != null ? record.getCells().get(fieldId) : null;
            BigDecimal num = extractNumber(cell);
            if (num != null) {
                sum = sum.add(num);
                n++;
                if (min == null || num.compareTo(min) < 0) {
                    min = num;
                }
                if (max == null || num.compareTo(max) > 0) {
                    max = num;
                }
            }
        }
        return new AggPart(records.size(), n == 0 ? null : sum, n, min, max);
    }

    /** 跨源合并部分聚合：count/sum 求和、avg 加权平均（与单源 avg 精度一致）、min/max 折叠 */
    private Object mergeValue(String aggregation, List<AggPart> parts) {
        return switch (aggregation) {
            case "count" -> parts.stream().mapToLong(AggPart::recordCount).sum();
            case "sum" -> {
                BigDecimal total = BigDecimal.ZERO;
                boolean any = false;
                for (AggPart part : parts) {
                    if (part.numericCount() > 0) {
                        total = total.add(part.sum());
                        any = true;
                    }
                }
                yield any ? total : null;
            }
            case "avg" -> {
                BigDecimal total = BigDecimal.ZERO;
                long n = 0;
                for (AggPart part : parts) {
                    if (part.numericCount() > 0) {
                        total = total.add(part.sum());
                        n += part.numericCount();
                    }
                }
                yield n == 0 ? null : total.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
            }
            case "min" -> {
                BigDecimal result = null;
                for (AggPart part : parts) {
                    if (part.min() != null && (result == null || part.min().compareTo(result) < 0)) {
                        result = part.min();
                    }
                }
                yield result;
            }
            case "max" -> {
                BigDecimal result = null;
                for (AggPart part : parts) {
                    if (part.max() != null && (result == null || part.max().compareTo(result) > 0)) {
                        result = part.max();
                    }
                }
                yield result;
            }
            default -> throw new BusinessException("不支持的聚合方式: " + aggregation);
        };
    }

    // ==================== AI 一键生成仪表盘 ====================

    /** AI 规划的组件类型 → 默认布局（12 列栅格 span + 高度 px） */
    private static final Map<String, int[]> AI_WIDGET_LAYOUT = Map.ofEntries(
            Map.entry("stat_number", new int[]{2, 140}),
            Map.entry("metric_card", new int[]{2, 150}),
            Map.entry("progress", new int[]{2, 150}),
            Map.entry("bar", new int[]{3, 260}),
            Map.entry("line", new int[]{3, 260}),
            Map.entry("hbar", new int[]{3, 260}),
            Map.entry("area", new int[]{3, 260}),
            Map.entry("pie", new int[]{3, 280}),
            Map.entry("donut", new int[]{3, 280}),
            Map.entry("radar", new int[]{3, 300}),
            Map.entry("funnel", new int[]{3, 280}),
            Map.entry("treemap", new int[]{4, 280}),
            Map.entry("combo", new int[]{4, 280}),
            Map.entry("rank", new int[]{3, 300}));

    private record AiWidgetPlan(String type, String title, Long tableId, Long dimensionFieldId,
                                String granularity, Long metricFieldId, String aggregation,
                                String sort, Integer limit, Integer span) {
    }

    private record AiRowPlan(String title, List<AiWidgetPlan> widgets) {
    }

    private record AiDashboardPlan(String name, List<AiRowPlan> rows) {
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long aiGenerate(Long baseId, String description, Long userId) {
        List<BitableTable> tables = tableMapper.selectList(new LambdaQueryWrapper<BitableTable>()
                .eq(BitableTable::getBaseId, baseId)
                .orderByAsc(BitableTable::getId));
        if (tables.isEmpty()) {
            throw new BusinessException("当前多维表格还没有数据表，请先创建数据表");
        }
        if (tables.size() > 6) {
            tables = tables.subList(0, 6);
        }

        String tableContext = buildTableContext(tables);
        String systemPrompt = buildAiSystemPrompt();
        String userMessage = "数据表结构如下：\n" + tableContext
                + (description != null && !description.isBlank()
                        ? "\n\n用户关注的分析重点：" + description
                        : "\n\n用户没有额外要求，请根据表结构自行设计最有价值的仪表盘。");

        String raw = callChat(systemPrompt, userMessage);
        AiDashboardPlan plan = parseAiPlan(raw);
        if (plan == null || plan.rows() == null || plan.rows().isEmpty()) {
            throw new BusinessException("AI 未能生成有效的仪表盘方案，请重试或补充描述");
        }

        Map<Long, BitableTable> tableMap = tables.stream()
                .collect(Collectors.toMap(BitableTable::getId, t -> t));
        List<Map<String, Object>> widgetPayloads = new ArrayList<>();
        List<Map<String, Object>> layoutRows = new ArrayList<>();
        int rowIdx = 0;
        for (AiRowPlan row : plan.rows()) {
            if (rowIdx >= 10 || row == null || row.widgets() == null || row.widgets().isEmpty()) {
                continue;
            }
            String rowId = "r_ai_" + (++rowIdx);
            List<Object> rowWidgetIds = new ArrayList<>();
            for (AiWidgetPlan w : row.widgets()) {
                Map<String, Object> payload = buildAiWidgetPayload(rowId, w, tableMap);
                if (payload == null || widgetPayloads.size() >= MAX_WIDGETS) {
                    continue;
                }
                widgetPayloads.add(payload);
                rowWidgetIds.add(payload.get("id"));
            }
            if (!rowWidgetIds.isEmpty()) {
                String title = row.title() != null && !row.title().isBlank() ? row.title().trim() : null;
                if (title != null && title.length() > 60) {
                    title = title.substring(0, 60);
                }
                Map<String, Object> layoutRow = new LinkedHashMap<>();
                layoutRow.put("id", rowId);
                layoutRow.put("title", title);
                layoutRow.put("widgets", rowWidgetIds);
                layoutRows.add(layoutRow);
            }
        }
        if (widgetPayloads.isEmpty()) {
            throw new BusinessException("AI 生成的方案没有可用的组件，请重试或补充描述");
        }

        Long dashboardId = create(baseId, plan.name() != null && !plan.name().isBlank() ? plan.name().trim() : "AI 仪表盘", userId);
        saveWidgets(dashboardId, widgetPayloads, Map.of("rows", layoutRows));
        return dashboardId;
    }

    /** 汇总 Base 下各表的字段结构供 LLM 参考（含单选选项与记录数） */
    private String buildTableContext(List<BitableTable> tables) {
        StringBuilder sb = new StringBuilder();
        for (BitableTable table : tables) {
            long recordCount = 0;
            try {
                RecordQueryDTO probe = new RecordQueryDTO();
                probe.setPageNum(1);
                probe.setPageSize(1);
                recordCount = recordService.queryRecords(table.getId(), probe).getTotal();
            } catch (Exception e) {
                log.debug("统计表记录数失败: tableId={}", table.getId(), e);
            }
            sb.append("表 id=").append(table.getId()).append("「").append(table.getName()).append("」（").append(recordCount).append(" 条记录）\n");
            List<BitableField> fields = fieldMapper.selectList(new LambdaQueryWrapper<BitableField>()
                    .eq(BitableField::getTableId, table.getId())
                    .orderByAsc(BitableField::getSortOrder));
            int count = 0;
            for (BitableField field : fields) {
                if (++count > 25) {
                    sb.append("- （其余字段略）\n");
                    break;
                }
                sb.append("- 字段 id=").append(field.getId()).append("「").append(field.getName()).append("」 ")
                        .append(field.getFieldType());
                String options = extractFieldOptions(field);
                if (!options.isEmpty()) {
                    sb.append("（选项: ").append(options).append("）");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /** 提取单选/多选字段的选项文本，供 LLM 理解字段语义 */
    private String extractFieldOptions(BitableField field) {
        if (field.getConfig() == null || field.getConfig().isBlank()) {
            return "";
        }
        try {
            Object config = BitableJsonUtils.parseJson(field.getConfig());
            if (!(config instanceof Map<?, ?> map) || !(map.get("options") instanceof List<?> list)) {
                return "";
            }
            List<String> labels = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> opt && opt.get("label") != null) {
                    labels.add(String.valueOf(opt.get("label")));
                }
                if (labels.size() >= 12) {
                    break;
                }
            }
            return String.join("/", labels);
        } catch (Exception e) {
            return "";
        }
    }

    private String buildAiSystemPrompt() {
        StringBuilder typeList = new StringBuilder();
        for (Map.Entry<String, int[]> e : AI_WIDGET_LAYOUT.entrySet()) {
            typeList.append(e.getKey()).append(" ");
        }
        return """
                你是一个多维表格仪表盘设计助手。根据给出的数据表结构（字段 id、名称、类型、选项）与用户关注点，设计一个可视化仪表盘。

                可用组件类型（type 取值）：
                %s
                - stat_number/metric_card：单值统计卡（记录数或字段聚合）
                - progress：进度图（单值指标 / 目标值）
                - bar/line/hbar/area：按维度分组的坐标图；pie/donut：占比；combo：柱线双指标；radar/funnel/treemap：多维对比
                - rank：排行榜（默认按指标降序 TopN）

                聚合方式 aggregation：count=记录数（无需字段）、sum/avg/max/min（必须给数值类字段：数字/货币/进度/评分/自动编号/公式）。
                日期字段做维度时 granularity 取 day/week/month。

                输出要求（严格 JSON，不要任何解释或代码块标记）：
                {"name":"仪表盘名称","rows":[{"title":"分组标题或null","widgets":[
                  {"type":"组件类型","title":"中文标题","tableId":表id,"dimensionFieldId":维度字段id或null,
                   "granularity":null,"metricFieldId":数值字段id或null,"aggregation":"count","sort":"default",
                   "limit":null,"span":2}]}]}

                设计规则：
                1. 只允许使用给出的表 id 与字段 id，禁止编造
                2. 2-3 个分组行，每行 2-4 个组件，总共 5-10 个组件；每行以 1-3 个统计数字类组件开头
                3. span 取值：统计数字类组件 2；图表类组件 3 或 4
                4. 优先使用有业务语义的维度（单选、人员、日期），数值字段用 sum/avg 聚合
                5. limit 仅 rank 需要（如 5 或 10）；sort 图表类可按 desc 让重点组排前
                """.formatted(typeList);
    }

    private String callChat(String systemPrompt, String userMessage) {
        LlmModelResolver.ResolvedModel resolved = llmModelResolver.resolveFirst(LlmApplicationCode.BITABLE_AI);
        if (resolved == null) {
            throw new BusinessException("请先在 LLM 应用管理中配置「多维表格 AI」对话模型");
        }
        LlmGatewayConfig.Provider provider = llmModelResolver.toGatewayProvider(resolved);
        LlmGateway.ChatResult result = llmGateway.chatWithProvider(provider, systemPrompt, userMessage);
        return result.getContent();
    }

    private AiDashboardPlan parseAiPlan(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline >= 0) {
                s = s.substring(firstNewline + 1);
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3).trim();
            }
        }
        try {
            return objectMapper.readValue(s, AiDashboardPlan.class);
        } catch (Exception e) {
            int start = s.indexOf('{');
            int end = s.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return objectMapper.readValue(s.substring(start, end + 1), AiDashboardPlan.class);
                } catch (Exception ex) {
                    log.debug("AI 仪表盘方案 JSON 片段提取失败", ex);
                }
            }
            log.error("解析 AI 仪表盘方案失败: {}", s, e);
            return null;
        }
    }

    /** 校验 AI 方案中的单个组件并转为保存载荷；非法组件返回 null（跳过而非失败） */
    private Map<String, Object> buildAiWidgetPayload(String rowId, AiWidgetPlan w, Map<Long, BitableTable> tableMap) {
        if (w == null || w.type() == null) {
            return null;
        }
        String type = w.type().trim();
        if (!VALID_WIDGET_TYPES.contains(type) || STATIC_WIDGET_TYPES.contains(type)) {
            return null;
        }
        BitableTable table = w.tableId() != null ? tableMap.get(w.tableId()) : null;
        if (table == null) {
            return null;
        }
        // 字段归属校验
        Long dimensionFieldId = fieldBelongsToTable(w.dimensionFieldId(), table.getId()) ? w.dimensionFieldId() : null;
        Long metricFieldId = fieldBelongsToTable(w.metricFieldId(), table.getId()) ? w.metricFieldId() : null;

        String aggregation = w.aggregation() == null ? "count" : w.aggregation().trim().toLowerCase(Locale.ROOT);
        if (!VALID_AGGREGATIONS.contains(aggregation)) {
            aggregation = "count";
        }
        if (!"count".equals(aggregation) && metricFieldId == null) {
            aggregation = "count";
        }
        boolean singleValue = SINGLE_VALUE_TYPES.contains(type);
        if (singleValue && "progress".equals(type) && "count".equals(aggregation)) {
            // 进度图必须有数值指标才有意义，退化为统计数字
            type = "stat_number";
        }

        int[] layout = AI_WIDGET_LAYOUT.getOrDefault(type, new int[]{3, 260});
        int span = w.span() != null ? Math.max(2, Math.min(6, w.span())) : layout[0];
        Map<String, Object> dataSource = new LinkedHashMap<>();
        dataSource.put("tableId", table.getId());
        if (!singleValue) {
            if (dimensionFieldId != null) {
                Map<String, Object> dimension = new LinkedHashMap<>();
                dimension.put("fieldId", dimensionFieldId);
                String granularity = w.granularity() != null ? w.granularity().trim().toLowerCase(Locale.ROOT) : "auto";
                dimension.put("granularity", VALID_GRANULARITIES.contains(granularity) ? granularity : "auto");
                dataSource.put("dimension", dimension);
            }
            dataSource.put("sort", "desc".equalsIgnoreCase(w.sort()) ? "desc"
                    : "asc".equalsIgnoreCase(w.sort()) ? "asc" : "rank".equals(type) ? "desc" : "default");
            if (w.limit() != null && w.limit() > 0) {
                dataSource.put("limit", Math.min(w.limit(), MAX_GROUPS));
            }
        }
        List<Map<String, Object>> metrics = new ArrayList<>();
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("fieldId", "count".equals(aggregation) ? null : metricFieldId);
        metric.put("aggregation", aggregation);
        metrics.add(metric);
        dataSource.put("metrics", metrics);

        String title = w.title() != null && !w.title().isBlank() ? w.title().trim() : type;
        if (title.length() > 200) {
            title = title.substring(0, 200);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", "tmp_ai_" + UUID.randomUUID().toString().substring(0, 8));
        payload.put("type", type);
        payload.put("title", title);
        payload.put("dataSourceConfig", dataSource);
        Map<String, Object> display = new LinkedHashMap<>();
        display.put("colorScheme", "default");
        payload.put("displayConfig", display);
        payload.put("layoutConfig", Map.of("rowId", rowId, "span", span, "height", layout[1]));
        return payload;
    }

    private boolean fieldBelongsToTable(Long fieldId, Long tableId) {
        if (fieldId == null) {
            return false;
        }
        BitableField field = fieldMapper.selectById(fieldId);
        return field != null && tableId.equals(field.getTableId());
    }

    // ==================== 指标与聚合 ====================

    private record Metric(Long fieldId, String aggregation, String name, String fieldName) {
    }

    /** 从 dataSourceConfig 解析指标列表（兼容旧版 fieldId+aggregation 单指标结构） */
    private List<Metric> resolveMetrics(Map<String, Object> dataSource, Map<Long, String> fieldNameCache) {
        List<Map<String, Object>> raw = extractMetrics(dataSource);
        if (raw.isEmpty()) {
            return List.of(new Metric(null, "count", "记录数", null));
        }
        List<Metric> metrics = new ArrayList<>();
        for (Map<String, Object> item : raw) {
            String aggregation = String.valueOf(item.getOrDefault("aggregation", "count")).toLowerCase(Locale.ROOT);
            Long fieldId = toLong(item.get("fieldId"));
            String name = "count".equals(aggregation) ? "记录数" : fieldName(fieldId, fieldNameCache);
            metrics.add(new Metric(fieldId, aggregation, name, null));
        }
        return metrics;
    }

    /**
     * 多数据源模式指标解析：优先按 fieldName 跨表对齐（各表同名各取本表 fieldId），
     * 未配 fieldName 时退回 fieldId（仅在字段确实属于某张表时生效）。
     */
    private List<Metric> resolveMultiSourceMetrics(Map<String, Object> dataSource) {
        List<Map<String, Object>> raw = extractMetrics(dataSource);
        if (raw.isEmpty()) {
            return List.of(new Metric(null, "count", "记录数", null));
        }
        List<Metric> metrics = new ArrayList<>();
        for (Map<String, Object> item : raw) {
            String aggregation = String.valueOf(item.getOrDefault("aggregation", "count")).toLowerCase(Locale.ROOT);
            String fieldName = item.get("fieldName") != null && !String.valueOf(item.get("fieldName")).isBlank()
                    ? String.valueOf(item.get("fieldName")) : null;
            Long fieldId = toLong(item.get("fieldId"));
            String name = "count".equals(aggregation) ? "记录数"
                    : (fieldName != null ? fieldName : fieldName(fieldId, new HashMap<>()));
            metrics.add(new Metric(fieldId, aggregation, name, fieldName));
        }
        return metrics;
    }

    /** 数据源列表（多数据源模式）；缺失或结构不符时返回空列表 */
    private List<Map<String, Object>> extractSources(Map<String, Object> dataSource) {
        Object sources = dataSource.get("sources");
        if (!(sources instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map) {
                result.add(castMap(item));
            }
        }
        return result;
    }

    /**
     * 按表 + 筛选加载记录（同一仪表盘请求内共享缓存，同表不同筛选互不污染）。
     * restrictedTables：访问者无读取权限的表（跟随访问者权限统计模式），
     * 直接返回空记录且不发查询、不写缓存——统计范围跟随访问者可见范围。
     */
    private List<BitableRecordVO> loadRecords(Map<String, List<BitableRecordVO>> cache, Long tableId, Object filterConfig,
                                              Set<Long> restrictedTables) {
        if (restrictedTables != null && restrictedTables.contains(tableId)) {
            return new ArrayList<>();
        }
        String key = tableId + "::" + (filterConfig != null ? String.valueOf(filterConfig) : "");
        return cache.computeIfAbsent(key, k -> {
            RecordQueryDTO query = new RecordQueryDTO();
            query.setPageNum(1);
            query.setPageSize(AGGREGATION_SCAN_LIMIT);
            // 组件级筛选随查询走统一查询引擎
            if (filterConfig != null) {
                query.setFilterConfig(filterConfig);
            }
            return recordService.queryRecords(tableId, query).getList();
        });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractMetrics(Map<String, Object> dataSource) {
        Object metrics = dataSource.get("metrics");
        if (metrics instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map) {
                    result.add((Map<String, Object>) item);
                }
            }
            return result;
        }
        // 旧版单指标结构
        Long fieldId = toLong(dataSource.get("fieldId"));
        String aggregation = String.valueOf(dataSource.getOrDefault("aggregation", "count"));
        if (fieldId == null && "count".equals(aggregation)) {
            return List.of();
        }
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("fieldId", fieldId);
        legacy.put("aggregation", aggregation);
        return List.of(legacy);
    }

    private String fieldName(Long fieldId, Map<Long, String> cache) {
        if (fieldId == null) {
            return "字段值";
        }
        return cache.computeIfAbsent(fieldId, id -> {
            BitableField field = fieldMapper.selectById(id);
            return field != null && field.getName() != null ? field.getName() : "字段值";
        });
    }

    private Object aggregate(List<BitableRecordVO> records, Metric metric) {
        if ("count".equals(metric.aggregation)) {
            return records.size();
        }
        List<BigDecimal> numbers = new ArrayList<>();
        for (BitableRecordVO record : records) {
            BitableCellValueVO cell = record.getCells() != null ? record.getCells().get(metric.fieldId) : null;
            BigDecimal num = extractNumber(cell);
            if (num != null) {
                numbers.add(num);
            }
        }
        if (numbers.isEmpty()) {
            return null;
        }
        return switch (metric.aggregation) {
            case "sum" -> {
                BigDecimal sum = BigDecimal.ZERO;
                for (BigDecimal n : numbers) {
                    sum = sum.add(n);
                }
                yield sum;
            }
            case "avg" -> {
                BigDecimal total = BigDecimal.ZERO;
                for (BigDecimal n : numbers) {
                    total = total.add(n);
                }
                yield total.divide(BigDecimal.valueOf(numbers.size()), 2, RoundingMode.HALF_UP);
            }
            case "min" -> numbers.stream().min(BigDecimal::compareTo).orElse(null);
            case "max" -> numbers.stream().max(BigDecimal::compareTo).orElse(null);
            default -> throw new BusinessException("不支持的聚合方式: " + metric.aggregation);
        };
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

    /**
     * 计算记录在维度字段上的分组键。
     * 多值字段（多选/人员等 valueJson 数组）展开为多组，一条记录可同时计入多个分组；
     * 日期字段按 granularity 归组（auto/day=天、week=ISO 周、month=月）。
     */
    private List<String> extractGroupKeys(BitableRecordVO record, Long fieldId, String granularity) {
        BitableCellValueVO cell = record.getCells() != null ? record.getCells().get(fieldId) : null;
        if (cell == null) {
            return List.of("(空)");
        }
        if (cell.getValueDate() != null) {
            return List.of(formatDateKey(cell.getValueDate(), granularity));
        }
        if (cell.getValueText() != null && !cell.getValueText().isBlank()) {
            return List.of(cell.getValueText());
        }
        if (cell.getValueNumber() != null) {
            return List.of(cell.getValueNumber().toPlainString());
        }
        Object json = cell.getValueJson();
        if (json instanceof Collection<?> col) {
            if (col.isEmpty()) {
                return List.of("(空)");
            }
            List<String> keys = new ArrayList<>();
            for (Object element : col) {
                keys.add(jsonElementKey(element));
            }
            return keys;
        }
        if (json != null) {
            return List.of(String.valueOf(json));
        }
        return List.of("(空)");
    }

    private String jsonElementKey(Object element) {
        if (element instanceof Map<?, ?> map && map.get("name") != null) {
            return String.valueOf(map.get("name"));
        }
        return String.valueOf(element);
    }

    private String formatDateKey(LocalDateTime date, String granularity) {
        return switch (granularity) {
            case "month" -> date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            case "week" -> {
                WeekFields weekFields = WeekFields.ISO;
                int week = date.get(weekFields.weekOfWeekBasedYear());
                int year = date.get(weekFields.weekBasedYear());
                yield String.format("%04d-W%02d", year, week);
            }
            default -> date.toLocalDate().toString();
        };
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

    private Integer toInt(Object value, Integer fallback) {
        Long l = toLong(value);
        return l != null ? l.intValue() : fallback;
    }
}
