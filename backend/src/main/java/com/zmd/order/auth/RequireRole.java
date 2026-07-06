package com.zmd.order.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口角色权限注解
 *
 * 标在 Controller 方法或类上，指定允许访问的角色。
 * AuthInterceptor 会检查当前登录用户的角色是否匹配。
 *
 * 用法：
 *   @RequireRole("ADMIN")           // 仅 ADMIN
 *   @RequireRole({"ADMIN","APPROVER"}) // ADMIN 或 APPROVER
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    String[] value();
}
