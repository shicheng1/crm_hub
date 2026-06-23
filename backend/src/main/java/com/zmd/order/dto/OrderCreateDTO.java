package com.zmd.order.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class OrderCreateDTO {
    @NotBlank(message = "标题不能为空")
    private String title;
    private String content;
    /** 审批流模板ID */
    @NotNull(message = "请选择审批流程")
    private Long flowId;
}
