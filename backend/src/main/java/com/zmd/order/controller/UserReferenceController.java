package com.zmd.order.controller;

import com.zmd.order.auth.LoginUser;
import com.zmd.order.common.R;
import com.zmd.order.entity.User;
import com.zmd.order.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户只读参考信息接口（任意登录用户可访问）。
 *
 * 与 UserController（@RequireRole("ADMIN")）分离：列出用户仅用于
 * 展示审批人姓名 / 在流程中指定审批人，属于只读参考数据，不应要求 ADMIN 权限。
 * listActiveUsers() 已脱敏（password 置空，仅返回 id/username/role/dept 等）。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserReferenceController {

    private final UserService userService;

    @GetMapping("/list")
    public R<List<User>> list() {
        return R.ok(userService.listActiveUsers());
    }
}
