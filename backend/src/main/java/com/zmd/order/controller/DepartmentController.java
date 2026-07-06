package com.zmd.order.controller;

import com.zmd.order.common.R;
import com.zmd.order.entity.Department;
import com.zmd.order.entity.User;
import com.zmd.order.mapper.DepartmentMapper;
import com.zmd.order.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dept")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentMapper deptMapper;
    private final UserService userService;

    @GetMapping("/list")
    public R<List<Department>> list() {
        return R.ok(deptMapper.selectList(null));
    }

    @GetMapping("/users")
    public R<List<User>> users() {
        return R.ok(userService.listActiveUsers());
    }
}
