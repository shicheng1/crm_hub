package com.zmd.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zmd.order.auth.LoginUser;
import com.zmd.order.auth.RequireRole;
import com.zmd.order.common.PageQuery;
import com.zmd.order.common.R;
import com.zmd.order.dto.UserCreateDTO;
import com.zmd.order.dto.UserPasswordResetDTO;
import com.zmd.order.dto.UserStatusDTO;
import com.zmd.order.dto.UserUpdateDTO;
import com.zmd.order.entity.User;
import com.zmd.order.service.AuditLogService;
import com.zmd.order.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@RequireRole("ADMIN")
public class UserController {

    private final UserService userService;
    private final AuditLogService auditLogService;

    @GetMapping("/page")
    public R<IPage<User>> page(PageQuery query,
                               @RequestParam(required = false) String username,
                               @RequestParam(required = false) String role,
                               @RequestParam(required = false) Long deptId,
                               @RequestParam(required = false) Integer status) {
        return R.ok(userService.pageUsers(query.getPage(), query.getSize(), username, role, deptId, status));
    }

    @GetMapping("/list")
    public R<List<User>> list() {
        return R.ok(userService.listActiveUsers());
    }

    @PostMapping
    public R<Long> create(@Valid @RequestBody UserCreateDTO dto, HttpServletRequest request) {
        Long id = userService.createUser(dto);
        audit("CREATE", "USER", id, "创建用户: " + dto.getUsername(), request);
        return R.ok(id);
    }

    @PutMapping("/{id}")
    public R<?> update(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO dto, HttpServletRequest request) {
        userService.updateUser(id, dto);
        audit("UPDATE", "USER", id, "更新用户角色/部门/状态", request);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<?> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusDTO dto, HttpServletRequest request) {
        userService.updateStatus(id, dto);
        audit("STATUS_CHANGE", "USER", id, "修改用户状态: " + dto.getStatus(), request);
        return R.ok();
    }

    @PutMapping("/{id}/password")
    public R<?> resetPassword(@PathVariable Long id, @Valid @RequestBody UserPasswordResetDTO dto, HttpServletRequest request) {
        userService.resetPassword(id, dto);
        audit("RESET_PASSWORD", "USER", id, "重置用户密码", request);
        return R.ok();
    }

    private void audit(String action, String targetType, Long targetId, String detail, HttpServletRequest request) {
        LoginUser user = LoginUser.get();
        auditLogService.log(
                user != null ? user.getUserId() : null,
                user != null ? user.getUsername() : null,
                action, targetType, targetId, detail, getClientIp(request));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}
