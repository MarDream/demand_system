package com.demand.system.module.nl2sql.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * NL2SQL 数据问答能力配置。
 *
 * <p>对应 application-*.yml 中的 {@code nl2sql.*}。默认值即安全基线：
 * 只读、限行、限时、仅允许白名单业务表。</p>
 */
@Component
@ConfigurationProperties(prefix = "nl2sql")
public class Nl2SqlProperties {

    /** 是否启用数据问答（关闭后助手不再走 NL2SQL 分支） */
    private boolean enabled = true;

    /** 单次查询返回的最大行数（超出会被截断并在回答中提示） */
    private int maxRows = 200;

    /** 注入给 LLM 用于总结的行数上限（避免超长提示词） */
    private int summaryRows = 60;

    /** SQL 执行超时（秒） */
    private int queryTimeoutSeconds = 15;

    /** Schema 目录缓存时长（分钟） */
    private int schemaCacheMinutes = 10;

    /**
     * 允许查询的业务表白名单（表名小写）。
     * 为空时使用 {@link com.demand.system.module.nl2sql.schema.Nl2SqlSemantics#defaultAllowedTables()}。
     */
    private List<String> allowedTables = List.of();

    /**
     * 额外禁止输出的列名（小写，模糊匹配：列名包含任一关键字即剔除）。
     */
    private List<String> deniedColumnKeywords = List.of(
            "password", "passwd", "secret", "api_key", "apikey", "access_key", "accesskey",
            "private_key", "privatekey", "token", "credential", "salt", "signature"
    );

    /** 是否允许自动路由：通用模式下命中数据类问句时自动切换到 NL2SQL */
    private boolean autoRouteEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }

    public int getSummaryRows() {
        return summaryRows;
    }

    public void setSummaryRows(int summaryRows) {
        this.summaryRows = summaryRows;
    }

    public int getQueryTimeoutSeconds() {
        return queryTimeoutSeconds;
    }

    public void setQueryTimeoutSeconds(int queryTimeoutSeconds) {
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    public int getSchemaCacheMinutes() {
        return schemaCacheMinutes;
    }

    public void setSchemaCacheMinutes(int schemaCacheMinutes) {
        this.schemaCacheMinutes = schemaCacheMinutes;
    }

    public List<String> getAllowedTables() {
        return allowedTables;
    }

    public void setAllowedTables(List<String> allowedTables) {
        this.allowedTables = allowedTables;
    }

    public List<String> getDeniedColumnKeywords() {
        return deniedColumnKeywords;
    }

    public void setDeniedColumnKeywords(List<String> deniedColumnKeywords) {
        this.deniedColumnKeywords = deniedColumnKeywords;
    }

    public boolean isAutoRouteEnabled() {
        return autoRouteEnabled;
    }

    public void setAutoRouteEnabled(boolean autoRouteEnabled) {
        this.autoRouteEnabled = autoRouteEnabled;
    }
}
