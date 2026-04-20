package com.demand.system.module.file.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_records")
public class FileRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String originalName;

    private String storageName;

    private Long fileSize;

    private String contentType;

    private String bucketName;

    private Long uploaderId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
