package com.demand.system.module.bitable.dto;

/**
 * 目录树叶子移动请求：{ kind, id, targetGroupId }
 *
 * <p>只移动单个叶子（数据表 / 仪表盘）到目标 Base 分组，
 * 不影响同 Base 下的其它叶子。</p>
 */
public class BitableLeafMoveRequest {

    /** 叶子类型：table / dashboard */
    private String kind;

    /** 叶子 id */
    private Long id;

    /** 目标 Base 分组 id，null=移到根层级（未分组） */
    private Long targetGroupId;

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

    public Long getTargetGroupId() {
        return targetGroupId;
    }

    public void setTargetGroupId(Long targetGroupId) {
        this.targetGroupId = targetGroupId;
    }
}
