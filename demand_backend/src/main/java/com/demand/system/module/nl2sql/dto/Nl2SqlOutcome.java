package com.demand.system.module.nl2sql.dto;

import java.util.List;

/**
 * NL2SQL 链路执行结果。
 *
 * @param isDataQuery 是否判定为数据查询（false 时助手层应回退到其它链路）
 * @param success     是否成功产出答案
 * @param dataResult  结构化查询结果（成功且为数据查询时非空）
 * @param warnings    降级/提示信息（如结果被截断、范围过滤生效）
 * @param errorMessage 失败原因（success=false 时有值）
 */
public record Nl2SqlOutcome(
        boolean isDataQuery,
        boolean success,
        DataQueryResult dataResult,
        List<String> warnings,
        String errorMessage
) {
    public static Nl2SqlOutcome notDataQuery() {
        return new Nl2SqlOutcome(false, false, null, List.of(), null);
    }

    public static Nl2SqlOutcome failure(String message) {
        return new Nl2SqlOutcome(true, false, null, List.of(), message);
    }

    public static Nl2SqlOutcome success(DataQueryResult result, List<String> warnings) {
        return new Nl2SqlOutcome(true, true, result, warnings, null);
    }
}
