package com.demand.system.module.bitable.service;

import com.demand.system.module.bitable.dto.ViewShareCreateDTO;
import com.demand.system.module.bitable.dto.ViewShareVO;

import java.util.Map;

/**
 * 多维表格-视图分享 Service（只读链接）
 */
public interface BitableViewShareService {

    /**
     * 创建/更新视图分享（同一视图重复分享覆盖配置，token 不变）
     */
    ViewShareVO share(Long viewId, ViewShareCreateDTO dto, Long userId);

    /**
     * 查询视图的分享信息（未分享返回 null）
     */
    ViewShareVO getByViewId(Long viewId);

    /**
     * 启用/停用分享
     */
    void updateStatus(Long viewId, boolean enabled);

    /**
     * 获取公开只读视图数据（匿名访问，凭 token）
     * 字段按视图配置脱敏（剔除隐藏列/AI提示词/内部配置），记录剔除操作人信息
     *
     * @param token    分享令牌
     * @param pageNum  页码
     * @param pageSize 页大小（上限 100）
     * @return 脱敏后的视图数据；token 无效返回 null
     */
    Map<String, Object> getPublicViewData(String token, Integer pageNum, Integer pageSize);
}
