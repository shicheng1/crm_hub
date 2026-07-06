package com.zmd.order.dto;

import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserDTOValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createUserShouldRequireUsernamePasswordAndRole() {
        UserCreateDTO dto = new UserCreateDTO();

        Set<ConstraintViolation<UserCreateDTO>> violations = validator.validate(dto);

        assertEquals(3, violations.size());
        assertTrue(violations.stream().anyMatch(v -> "用户名不能为空".equals(v.getMessage())));
        assertTrue(violations.stream().anyMatch(v -> "密码不能为空".equals(v.getMessage())));
        assertTrue(violations.stream().anyMatch(v -> "角色不能为空".equals(v.getMessage())));
    }

    @Test
    void updateUserShouldRequireRole() {
        UserUpdateDTO dto = new UserUpdateDTO();
        dto.setDeptId(1L);

        Set<ConstraintViolation<UserUpdateDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertTrue(violations.stream().anyMatch(v -> "角色不能为空".equals(v.getMessage())));
    }

    @Test
    void resetPasswordShouldRequirePassword() {
        UserPasswordResetDTO dto = new UserPasswordResetDTO();

        Set<ConstraintViolation<UserPasswordResetDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertTrue(violations.stream().anyMatch(v -> "新密码不能为空".equals(v.getMessage())));
    }
}
