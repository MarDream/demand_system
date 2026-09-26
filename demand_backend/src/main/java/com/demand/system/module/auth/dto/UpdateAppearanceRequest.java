package com.demand.system.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 个人设置-外观配置（主题模式/主题色/圆角档位）。前端跟随当前登录用户持久化。
 */
public class UpdateAppearanceRequest {

    /** 主题模式：light / dark / auto（跟随系统） */
    @NotBlank(message = "主题模式不能为空")
    @Pattern(regexp = "^(light|dark|auto)$", message = "主题模式不合法")
    private String mode;

    /** 主题色（#RRGGBB） */
    @NotBlank(message = "主题色不能为空")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "主题色格式不正确")
    private String primary;

    /** 圆角档位：none / soft / round */
    @NotBlank(message = "圆角档位不能为空")
    @Pattern(regexp = "^(none|soft|round)$", message = "圆角档位不合法")
    private String radius;

    /** 侧边栏风格：classic / floating / dark / glass / dual-rail / collapsible / grouped（可选，缺省 classic） */
    @Pattern(regexp = "^(classic|floating|dark|glass|dual-rail|collapsible|grouped)$", message = "侧边栏风格不合法")
    private String sidebar;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getPrimary() { return primary; }
    public void setPrimary(String primary) { this.primary = primary; }
    public String getRadius() { return radius; }
    public void setRadius(String radius) { this.radius = radius; }
    public String getSidebar() { return sidebar; }
    public void setSidebar(String sidebar) { this.sidebar = sidebar; }
}
