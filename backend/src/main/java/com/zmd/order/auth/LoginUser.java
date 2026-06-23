package com.zmd.order.auth;

import lombok.Data;

/**
 * 当前登录用户上下文（通过 ThreadLocal 传递）
 */
@Data
public class LoginUser {
    private Long userId;
    private String username;
    private String role;

    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    public static void set(LoginUser user) {
        CONTEXT.set(user);
    }

    public static LoginUser get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
