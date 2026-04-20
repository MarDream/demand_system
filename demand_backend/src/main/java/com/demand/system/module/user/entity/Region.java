package com.demand.system.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.List;

@Data
@TableName("regions")
public class Region {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long parentId;

    private String code;

    private Integer sortOrder;

    @TableField(exist = false)
    private List<Region> children;
}
