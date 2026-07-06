package com.zmd.order.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class UserUpdateDTO {
    @NotBlank(message = "角色不能为空")
    private String role;

    private Long deptId;

    private Integer status;
}
