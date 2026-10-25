package com.demand.system.module.user.dto;

import java.util.List;

/**
 * 批量管理用户入参（批量启用 / 批量停用 / 批量删除）
 */
public class BatchUserActionDTO {

    private List<Long> ids;

    /** 目标状态，仅批量启用/停用使用：active / inactive */
    private String status;

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
