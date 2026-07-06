package com.zmd.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zmd.order.common.BusinessException;
import com.zmd.order.dto.UserCreateDTO;
import com.zmd.order.dto.UserPasswordResetDTO;
import com.zmd.order.dto.UserStatusDTO;
import com.zmd.order.dto.UserUpdateDTO;
import com.zmd.order.entity.Department;
import com.zmd.order.entity.User;
import com.zmd.order.mapper.DepartmentMapper;
import com.zmd.order.mapper.UserMapper;
import com.zmd.order.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public IPage<User> pageUsers(int page, int size, String username, String role, Long deptId, Integer status) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.trim().isEmpty()) {
            wrapper.like(User::getUsername, username.trim());
        }
        if (role != null && !role.trim().isEmpty()) {
            wrapper.eq(User::getRole, role.trim());
        }
        if (deptId != null) {
            wrapper.eq(User::getDeptId, deptId);
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getCreateTime);
        IPage<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        fillDeptNameAndHidePassword(result.getRecords());
        return result;
    }

    @Override
    public List<User> listActiveUsers() {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getStatus, 1)
                        .orderByAsc(User::getId));
        fillDeptNameAndHidePassword(users);
        return users;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(UserCreateDTO dto) {
        long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (exists > 0) {
            throw new BusinessException(400, "用户名已存在");
        }
        validateRole(dto.getRole());
        validateStatus(dto.getStatus());

        User user = new User();
        user.setUsername(dto.getUsername().trim());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(dto.getRole().trim());
        user.setDeptId(dto.getDeptId());
        user.setStatus(dto.getStatus());
        userMapper.insert(user);
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long id, UserUpdateDTO dto) {
        User user = getRequiredUser(id);
        validateRole(dto.getRole());
        if (dto.getStatus() != null) {
            validateStatus(dto.getStatus());
            user.setStatus(dto.getStatus());
        }
        user.setRole(dto.getRole().trim());
        user.setDeptId(dto.getDeptId());
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, UserStatusDTO dto) {
        User user = getRequiredUser(id);
        validateStatus(dto.getStatus());
        user.setStatus(dto.getStatus());
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, UserPasswordResetDTO dto) {
        User user = getRequiredUser(id);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        userMapper.updateById(user);
    }

    private User getRequiredUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    private void validateRole(String role) {
        if (!"USER".equals(role) && !"APPROVER".equals(role) && !"ADMIN".equals(role)) {
            throw new BusinessException(400, "角色不合法");
        }
    }

    private void validateStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(400, "状态不合法");
        }
    }

    private void fillDeptNameAndHidePassword(List<User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<Department> departments = departmentMapper.selectList(null);
        Map<Long, String> deptNameMap = departments.stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
        for (User user : users) {
            user.setPassword(null);
            if (user.getDeptId() != null) {
                user.setDeptName(deptNameMap.getOrDefault(user.getDeptId(), ""));
            }
        }
    }
}
