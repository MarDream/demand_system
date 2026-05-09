package com.demand.system.module.rbac.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class MenuUpdateDTO extends MenuCreateDTO {

    private Long id;
}
