package com.demand.system.module.bitable.dto;

/**
 * 目录树叶子节点引用（数据表或仪表盘）。
 *
 * <p>目录树里数据表与仪表盘是同层级的兄弟节点，共用同一个 sort_order 序列，
 * 所以排序接口必须能同时表达两种类型。</p>
 */
public class BitableLeafOrderItemDTO {

    /** table / dashboard */
    private String kind;

    private Long id;

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
