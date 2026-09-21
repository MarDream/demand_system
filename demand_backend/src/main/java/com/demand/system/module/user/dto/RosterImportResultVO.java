package com.demand.system.module.user.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 花名册导入结果（参考钉钉导入结果反馈：成功 N 条 / 失败明细）
 */
public class RosterImportResultVO {

    private int successCount;

    private int failCount;

    /** 失败明细：第 N 行：原因 */
    private List<String> failures = new ArrayList<>();

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }

    public List<String> getFailures() {
        return failures;
    }

    public void setFailures(List<String> failures) {
        this.failures = failures;
    }
}
