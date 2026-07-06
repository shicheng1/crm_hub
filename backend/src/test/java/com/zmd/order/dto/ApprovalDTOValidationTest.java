package com.zmd.order.dto;

import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalDTOValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void missingOrderIdShouldFailValidation() {
        ApprovalDTO dto = new ApprovalDTO();
        dto.setApproved(Boolean.TRUE);

        Set<ConstraintViolation<ApprovalDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertTrue(violations.stream().anyMatch(v -> "工单ID不能为空".equals(v.getMessage())));
    }

    @Test
    void missingApprovalResultShouldFailValidation() {
        ApprovalDTO dto = new ApprovalDTO();
        dto.setOrderId(1001L);

        Set<ConstraintViolation<ApprovalDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertTrue(violations.stream().anyMatch(v -> "审批结果不能为空".equals(v.getMessage())));
    }

    @Test
    void validPayloadShouldPassValidation() {
        ApprovalDTO dto = new ApprovalDTO();
        dto.setOrderId(1001L);
        dto.setApproved(Boolean.FALSE);
        dto.setRemark("资料不完整");

        Set<ConstraintViolation<ApprovalDTO>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty());
    }
}
