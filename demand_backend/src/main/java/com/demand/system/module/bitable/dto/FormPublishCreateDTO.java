package com.demand.system.module.bitable.dto;

import java.time.LocalDateTime;

/**
 * 公开表单发布配置 DTO
 */
public class FormPublishCreateDTO {

    /** 过期时间（可空=永不过期） */
    private LocalDateTime expireAt;

    /** 提交总数上限（可空=不限制） */
    private Integer submitLimit;

    /** 访问密码（可空=无密码；传入则设置/更新密码） */
    private String password;

    /** 提交成功提示语 */
    private String successMessage;

    /** 提交后跳转 URL */
    private String redirectUrl;

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public Integer getSubmitLimit() {
        return submitLimit;
    }

    public void setSubmitLimit(Integer submitLimit) {
        this.submitLimit = submitLimit;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSuccessMessage() {
        return successMessage;
    }

    public void setSuccessMessage(String successMessage) {
        this.successMessage = successMessage;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }
}
