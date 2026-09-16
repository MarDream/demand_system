package com.demand.system.module.nl2sql.support;

import com.demand.system.module.nl2sql.config.Nl2SqlProperties;
import com.demand.system.module.nl2sql.dto.DataQueryColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读 SQL 执行器。
 *
 * <p>安全措施：连接置为 read-only、语句超时、最大行数限制（多取一行用于判断截断）。</p>
 */
@Component
public class SqlExecutor {

    private static final Logger log = LoggerFactory.getLogger(SqlExecutor.class);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final Nl2SqlProperties properties;

    public SqlExecutor(DataSource dataSource, Nl2SqlProperties properties) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.properties = properties;
    }

    /**
     * 执行只读查询。
     *
     * @param sql             已通过安全校验的 SQL
     * @param maxRows         返回行数上限
     * @param timeoutSeconds  语句超时（秒）
     */
    public ExecutionResult execute(String sql, int maxRows, int timeoutSeconds) {
        long start = System.currentTimeMillis();
        ExecutionResult result = jdbcTemplate.execute((ConnectionCallback<ExecutionResult>) connection -> {
            boolean previousReadOnly = false;
            try {
                previousReadOnly = connection.isReadOnly();
                connection.setReadOnly(true);
            } catch (Exception ignore) {
                // 部分驱动/连接池不支持切换 read-only，忽略即可
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                try {
                    statement.setQueryTimeout(Math.max(1, timeoutSeconds));
                } catch (Exception ignore) {
                    // 驱动不支持超时时忽略
                }
                try {
                    statement.setMaxRows(Math.max(1, maxRows) + 1);
                } catch (Exception ignore) {
                    // 忽略
                }
                try (ResultSet rs = statement.executeQuery()) {
                    return readResultSet(rs, maxRows);
                }
            } finally {
                try {
                    connection.setReadOnly(previousReadOnly);
                } catch (Exception ignore) {
                    // 忽略
                }
            }
        });
        long duration = System.currentTimeMillis() - start;
        log.info("NL2SQL 执行完成：{} 行，耗时 {} ms", result == null ? 0 : result.rows().size(), duration);
        return new ExecutionResult(
                result == null ? List.of() : result.columns(),
                result == null ? List.of() : result.rows(),
                result != null && result.truncated(),
                duration
        );
    }

    private ExecutionResult readResultSet(ResultSet rs, int maxRows) throws java.sql.SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<DataQueryColumn> columns = new ArrayList<>(columnCount);
        // 结果侧兜底：即便 SQL 通过了校验，也把敏感列从返回结果中剔除
        List<Integer> keptIndexes = new ArrayList<>(columnCount);
        List<String> deniedKeywords = properties.getDeniedColumnKeywords();
        for (int i = 1; i <= columnCount; i++) {
            String label = meta.getColumnLabel(i);
            if (label == null || label.isBlank()) {
                label = meta.getColumnName(i);
            }
            if (SqlSafetyValidator.isSensitiveColumn(label, deniedKeywords)) {
                log.warn("NL2SQL 结果列命中敏感关键字，已剔除：{}", label);
                continue;
            }
            keptIndexes.add(i);
            columns.add(new DataQueryColumn(label, label, mapType(meta.getColumnType(i))));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() >= maxRows) {
                truncated = true;
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (int index = 0; index < keptIndexes.size(); index++) {
                row.put(columns.get(index).field(), normalizeValue(rs.getObject(keptIndexes.get(index))));
            }
            rows.add(row);
        }
        return new ExecutionResult(columns, rows, truncated, 0L);
    }

    private String mapType(int sqlType) {
        return switch (sqlType) {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
                 Types.FLOAT, Types.REAL, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL,
                 Types.BIT, Types.BOOLEAN -> "number";
            case Types.DATE, Types.TIME, Types.TIME_WITH_TIMEZONE, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "date";
            default -> "string";
        };
    }

    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().format(DATE_TIME);
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime().toString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(DATE_TIME);
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        if (value instanceof LocalTime time) {
            return time.toString();
        }
        if (value instanceof byte[]) {
            return "[二进制数据]";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return value;
    }

    /**
     * 执行结果。
     *
     * @param columns   列定义
     * @param rows      数据行
     * @param truncated 是否被行数上限截断
     * @param durationMs 执行耗时
     */
    public record ExecutionResult(
            List<DataQueryColumn> columns,
            List<Map<String, Object>> rows,
            boolean truncated,
            long durationMs
    ) {
    }
}
