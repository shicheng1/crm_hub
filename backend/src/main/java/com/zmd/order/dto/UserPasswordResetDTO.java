package com.zmd.order.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class UserPasswordResetDTO {
    @NotBlank(message = "新密码不能为空")
    private String password;
}
