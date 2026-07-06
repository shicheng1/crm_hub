package com.zmd.order.dto;

import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderCreateDTOValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void blankTitleShouldFailValidation() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setTitle(" ");
        dto.setContent("采购电脑");
        dto.setFlowId(1L);

        Set<ConstraintViolation<OrderCreateDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertTrue(violations.stream().anyMatch(v -> "标题不能为空".equals(v.getMessage())));
    }

    @Test
    void missingFlowIdShouldFailValidation() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setTitle("采购申请");
        dto.setContent("采购电脑");

        Set<ConstraintViolation<OrderCreateDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertTrue(violations.stream().anyMatch(v -> "请选择审批流程".equals(v.getMessage())));
    }

    @Test
    void validPayloadShouldPassValidation() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setTitle("采购申请");
        dto.setContent("采购电脑");
        dto.setFlowId(1L);

        Set<ConstraintViolation<OrderCreateDTO>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty());
    }
}
