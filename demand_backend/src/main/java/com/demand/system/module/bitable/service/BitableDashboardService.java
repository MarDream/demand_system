package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.entity.BitableDashboard;
import com.demand.system.module.bitable.entity.BitableDashboardWidget;

import java.util.List;
import java.util.Map;

/**
 * 多维表格-仪表盘 Service
 */
public interface BitableDashboardService {

    /** 列出 Base 下的仪表盘 */
    List<BitableDashboard> listByBase(Long baseId);

    /** 创建仪表盘 */
    Long create(Long baseId, String name, Long userId);

    /** 重命名 */
    void rename(Long id, String name);

    /** 删除（级联删除组件） */
    void delete(Long id);

    /** 列出仪表盘组件 */
    List<BitableDashboardWidget> listWidgets(Long dashboardId);

    /** 全量保存组件配置（按 id 增量更新，保留未变更组件的标识；layoutConfig 为行式布局 JSON） */
    void saveWidgets(Long dashboardId, List<Map<String, Object>> widgets, Object layoutConfig);

    /**
     * AI 一键生成仪表盘：基于 Base 下数据表结构，由 LLM 设计组件与行式布局并落库
     *
     * @return 新建仪表盘 id
     */
    Long aiGenerate(Long baseId, String description, Long userId);

    /**
     * 获取仪表盘全部组件的聚合数据（按当前用户权限过滤后的数据计算）
     *
     * @return [{widgetId, type, title, data}]
     */
    List<Map<String, Object>> getDashboardData(Long dashboardId);

    /**
     * 仪表盘数据权限感知版本（供控制器使用）：
     * <ul>
     *   <li>full=基于全部数据统计（默认，未配置 dashboard_data 权限时）</li>
     *   <li>view=跟随访问者权限统计：访问者无权限查看的数据表，其记录不参与聚合（图表可见但统计为空）</li>
     *   <li>none=有不可查看数据时，图表不可见：组件任一来源表对访问者无读取权限，该组件标记 hidden</li>
     * </ul>
     * userId 为 null 时视作 full（内部调用/异步任务）。
     *
     * @return [{widgetId, type, title, data, hidden?}]
     */
    List<Map<String, Object>> getDashboardData(Long dashboardId, Long userId);
}
