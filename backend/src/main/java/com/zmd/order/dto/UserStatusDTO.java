package com.zmd.order.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class UserStatusDTO {
    @NotNull(message = "状态不能为空")
    private Integer status;
}
