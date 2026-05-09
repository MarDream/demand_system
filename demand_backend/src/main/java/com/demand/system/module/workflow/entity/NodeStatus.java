package com.demand.system.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("node_statuses")
public class NodeStatus {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String code;

    private String color;

    private Integer sortOrder;

    private Boolean isStart;

    private Boolean isEnd;

    private Boolean isCancel;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
