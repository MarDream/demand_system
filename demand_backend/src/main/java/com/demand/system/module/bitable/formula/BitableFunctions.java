package com.demand.system.module.bitable.formula;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 多维表格自定义 JEXL 函数
 * 通过 namespace "bf" 调用，如 bf:IF(condition, trueVal, falseVal)
 * 所有方法必须是 public static（JEXL namespace 要求）
 */
public class BitableFunctions {

    private BitableFunctions() {
    }

    // ===== 逻辑函数 =====

    public static Object IF(boolean condition, Object trueVal, Object falseVal) {
        return condition ? trueVal : falseVal;
    }

    public static Object IFS(Object... args) {
        for (int i = 0; i < args.length - 1; i += 2) {
            if (Boolean.TRUE.equals(args[i])) {
                return args[i + 1];
            }
        }
        return args.length % 2 == 1 ? args[args.length - 1] : null;
    }

    public static boolean AND(Object... args) {
        for (Object arg : args) {
            if (!toBool(arg)) {
                return false;
            }
        }
        return true;
    }

    public static boolean OR(Object... args) {
        for (Object arg : args) {
            if (toBool(arg)) {
                return true;
            }
        }
        return false;
    }

    public static boolean NOT(Object val) {
        return !toBool(val);
    }

    // ===== 文本函数 =====

    public static String CONCATENATE(Object... args) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            sb.append(arg != null ? arg.toString() : "");
        }
        return sb.toString();
    }

    public static String LEFT(String text, int len) {
        if (text == null) {
            return "";
        }
        return text.substring(0, Math.min(len, text.length()));
    }

    public static String RIGHT(String text, int len) {
        if (text == null) {
            return "";
        }
        return text.substring(Math.max(0, text.length() - len));
    }

    public static String MID(String text, int start, int len) {
        if (text == null) {
            return "";
        }
        int idx = Math.max(0, start - 1); // 1-based to 0-based
        int end = Math.min(idx + len, text.length());
        return idx >= text.length() ? "" : text.substring(idx, end);
    }

    public static int LEN(Object text) {
        return text == null ? 0 : text.toString().length();
    }

    public static int FIND(String search, String text) {
        if (text == null || search == null) {
            return 0;
        }
        return text.indexOf(search) + 1;
    }

    public static String REPLACE(String text, int start, int len, String replacement) {
        if (text == null) {
            return replacement != null ? replacement : "";
        }
        int idx = Math.max(0, start - 1);
        String rep = replacement != null ? replacement : "";
        return text.substring(0, idx) + rep + text.substring(Math.min(idx + len, text.length()));
    }

    // ===== 数学函数 =====

    public static double SUM(Object... args) {
        double sum = 0;
        for (Object arg : args) {
            sum += toNumber(arg);
        }
        return sum;
    }

    public static double AVERAGE(Object... args) {
        double sum = SUM(args);
        return args.length > 0 ? sum / args.length : 0;
    }

    public static double MAX(Object... args) {
        double max = Double.MIN_VALUE;
        for (Object arg : args) {
            max = Math.max(max, toNumber(arg));
        }
        return max;
    }

    public static double MIN(Object... args) {
        double min = Double.MAX_VALUE;
        for (Object arg : args) {
            min = Math.min(min, toNumber(arg));
        }
        return min;
    }

    public static double ROUND(double val, int precision) {
        double factor = Math.pow(10, precision);
        return Math.round(val * factor) / factor;
    }

    public static double CEILING(double val) {
        return Math.ceil(val);
    }

    public static double FLOOR(double val) {
        return Math.floor(val);
    }

    public static double MOD(double a, double b) {
        return b == 0 ? 0 : a % b;
    }

    // ===== 日期函数 =====

    public static LocalDate TODAY() {
        return LocalDate.now();
    }

    public static LocalDateTime NOW() {
        return LocalDateTime.now();
    }

    public static int YEAR(LocalDate date) {
        return date != null ? date.getYear() : 0;
    }

    public static int MONTH(LocalDate date) {
        return date != null ? date.getMonthValue() : 0;
    }

    public static int DAY(LocalDate date) {
        return date != null ? date.getDayOfMonth() : 0;
    }

    public static int WEEKDAY(LocalDate date) {
        return date != null ? date.getDayOfWeek().getValue() : 0;
    }

    // ===== 数组/聚合函数 =====

    public static String ARRAYJOIN(List<?> arr, String separator) {
        if (arr == null) {
            return "";
        }
        String sep = separator != null ? separator : ",";
        return arr.stream()
                .map(v -> v != null ? v.toString() : "")
                .reduce((a, b) -> a + sep + b)
                .orElse("");
    }

    public static int COUNTALL(List<?> arr) {
        return arr == null ? 0 : arr.size();
    }

    public static int COUNTA(List<?> arr) {
        if (arr == null) {
            return 0;
        }
        return (int) arr.stream()
                .filter(v -> v != null && !v.toString().isEmpty())
                .count();
    }

    public static long RECORD_ID(Long id) {
        return id != null ? id : 0;
    }

    public static Object BLANK() {
        return null;
    }

    // ===== 辅助方法 =====

    private static boolean toBool(Object val) {
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof Number n) {
            return n.doubleValue() != 0;
        }
        return val != null && !val.toString().isEmpty();
    }

    private static double toNumber(Object val) {
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        if (val instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
