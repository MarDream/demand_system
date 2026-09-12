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

    /** 全量保存组件配置（替换语义） */
    void saveWidgets(Long dashboardId, List<Map<String, Object>> widgets);

    /**
     * 获取仪表盘全部组件的聚合数据（按当前用户权限过滤后的数据计算）
     *
     * @return [{widgetId, type, title, data}]
     */
    List<Map<String, Object>> getDashboardData(Long dashboardId);
}
