package com.demand.system.module.bitable.annotation;

import com.demand.system.module.bitable.constant.MemberRole;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 多维表格权限校验注解
 * 标注在 Controller 方法上，自动校验当前用户是否有指定 Base 的最低角色权限
 * <p>
 * 使用示例：
 * <pre>
 * {@code @RequireBitableRole(value = MemberRole.EDITOR, baseIdParam = "baseId")}
 * public Result&lt;Void&gt; createTable(@PathVariable Long baseId, ...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireBitableRole {
    /**
     * 最低需要的角色
     */
    MemberRole value() default MemberRole.VIEWER;

    /**
     * baseId 参数名（从方法参数或路径变量中提取）
     */
    String baseIdParam() default "baseId";
}
