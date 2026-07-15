package com.demand.system.module.test;

import com.demand.system.module.rbac.support.RbacConstants;
import com.demand.system.module.rbac.support.RbacPermissionResolver;
import com.demand.system.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/debug")
public class DebugController {
    
    private final RbacPermissionResolver resolver;
    
    public DebugController(RbacPermissionResolver resolver) {
        this.resolver = resolver;
    }
    
    @GetMapping("/permissions")
    public Result<Map<String, Object>> checkPermissions() {
        Long userId = 1L;
        List<String> roles = resolver.resolveRoles(userId);
        List<String> permissions = resolver.resolvePermissions(userId, roles);
        
        return Result.success(Map.of(
            "userId", userId,
            "roles", roles,
            "totalPermissions", permissions.size(),
            "hasBitable", permissions.contains("menu:bitable"),
            "allPermissionsSize", RbacConstants.ALL_PERMISSION_CODES.size(),
            "constantHasBitable", RbacConstants.ALL_PERMISSION_CODES.contains("menu:bitable")
        ));
    }
}
