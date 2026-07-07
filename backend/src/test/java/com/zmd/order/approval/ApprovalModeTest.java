package com.zmd.order.approval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("会签/或签审批模式判定测试")
class ApprovalModeTest {

    @Test
    @DisplayName("或签(ANY)：任一审批人通过即视为步骤通过")
    void anyModeApprovedWhenAtLeastOne() {
        assertTrue(ApprovalModeEvaluator.isStepFullyApproved("ANY", 3, 1));
        assertTrue(ApprovalModeEvaluator.isStepFullyApproved("ANY", 3, 3));
    }

    @Test
    @DisplayName("或签(ANY)：无人通过则步骤未通过")
    void anyModeNotApprovedWhenNone() {
        assertFalse(ApprovalModeEvaluator.isStepFullyApproved("ANY", 3, 0));
    }

    @Test
    @DisplayName("会签(ALL)：全部审批人通过才视为步骤通过")
    void allModeApprovedWhenAllPassed() {
        assertTrue(ApprovalModeEvaluator.isStepFullyApproved("ALL", 3, 3));
        assertTrue(ApprovalModeEvaluator.isStepFullyApproved("all", 2, 2));
    }

    @Test
    @DisplayName("会签(ALL)：仍有审批人未通过时步骤未通过（停留等待他人）")
    void allModeNotApprovedWhenPartial() {
        assertFalse(ApprovalModeEvaluator.isStepFullyApproved("ALL", 3, 2));
        assertFalse(ApprovalModeEvaluator.isStepFullyApproved("ALL", 2, 1));
    }

    @Test
    @DisplayName("会签(ALL)：未配置审批人视为未通过")
    void allModeNotApprovedWhenNoApprover() {
        assertFalse(ApprovalModeEvaluator.isStepFullyApproved("ALL", 0, 0));
    }

    @Test
    @DisplayName("审批模式为空/null/未知时按或签(ANY)处理")
    void nullOrUnknownModeTreatedAsAny() {
        assertTrue(ApprovalModeEvaluator.isStepFullyApproved(null, 3, 1));
        assertTrue(ApprovalModeEvaluator.isStepFullyApproved("", 3, 1));
        assertTrue(ApprovalModeEvaluator.isStepFullyApproved("unknown", 3, 1));
        assertFalse(ApprovalModeEvaluator.isStepFullyApproved(null, 3, 0));
    }
}
