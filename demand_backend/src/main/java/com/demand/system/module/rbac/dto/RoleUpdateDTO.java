package com.demand.system.module.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleUpdateDTO {

    private Long id;

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 50, message = "角色编码不能超过50个字符")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "角色编码仅支持大写字母、数字和下划线，且必须以字母开头")
    private String code;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称不能超过100个字符")
    private String name;

    @Size(max = 500, message = "角色描述不能超过500个字符")
    private String description;
}
