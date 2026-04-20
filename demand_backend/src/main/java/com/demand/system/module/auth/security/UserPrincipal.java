package com.demand.system.module.auth.security;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
public class UserPrincipal implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private List<String> roles;
}
