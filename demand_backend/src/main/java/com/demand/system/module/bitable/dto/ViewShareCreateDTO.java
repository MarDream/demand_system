package com.demand.system.module.bitable.dto;

import java.time.LocalDateTime;

/**
 * 视图分享创建 DTO
 */
public class ViewShareCreateDTO {

    private LocalDateTime expireAt;

    private Boolean allowDownload;

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public Boolean getAllowDownload() {
        return allowDownload;
    }

    public void setAllowDownload(Boolean allowDownload) {
        this.allowDownload = allowDownload;
    }
}
