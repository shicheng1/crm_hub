package com.zmd.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zmd.order.common.R;
import com.zmd.order.entity.Department;
import com.zmd.order.entity.User;
import com.zmd.order.mapper.DepartmentMapper;
import com.zmd.order.mapper.UserMapper;
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
    private final UserMapper userMapper;

    @GetMapping("/list")
    public R<List<Department>> list() {
        return R.ok(deptMapper.selectList(null));
    }

    @GetMapping("/users")
    public R<List<User>> users() {
        List<User> users = userMapper.selectList(null);
        for (User user : users) {
            user.setPassword(null);  // 不返回密码
            if (user.getDeptId() != null) {
                Department dept = deptMapper.selectById(user.getDeptId());
                user.setDeptName(dept != null ? dept.getName() : "");
            }
        }
        return R.ok(users);
    }
}
