package com.demand.system.module.auth.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 用户自助绑定组织请求
 */
public class BindOrgRequest {

    @NotNull(message = "组织ID不能为空")
    private Long orgId;

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }
}
