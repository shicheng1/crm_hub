package com.zmd.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zmd.order.dto.UserCreateDTO;
import com.zmd.order.dto.UserPasswordResetDTO;
import com.zmd.order.dto.UserStatusDTO;
import com.zmd.order.dto.UserUpdateDTO;
import com.zmd.order.entity.User;

import java.util.List;

public interface UserService {
    IPage<User> pageUsers(int page, int size, String username, String role, Long deptId, Integer status);

    List<User> listActiveUsers();

    Long createUser(UserCreateDTO dto);

    void updateUser(Long id, UserUpdateDTO dto);

    void updateStatus(Long id, UserStatusDTO dto);

    void resetPassword(Long id, UserPasswordResetDTO dto);
}
