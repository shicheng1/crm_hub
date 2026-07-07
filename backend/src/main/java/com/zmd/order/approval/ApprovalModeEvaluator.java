package com.zmd.order.approval;

/**
 * 审批模式判定工具（会签 / 或签）。
 *
 * <p>纯逻辑、无数据库依赖，便于单元测试独立覆盖：
 * <ul>
 *     <li>ANY（或签）：任一审批人通过即视为本步骤通过，可进入下一步</li>
 *     <li>ALL（会签）：需要配置的全部审批人都通过，才视为本步骤通过</li>
 * </ul>
 */
public final class ApprovalModeEvaluator {

    public static final String MODE_ANY = "ANY";
    public static final String MODE_ALL = "ALL";

    private ApprovalModeEvaluator() {
    }

    /**
     * 判断某审批步骤是否已整体通过（可进入下一步）。
     *
     * @param approveMode   审批模式；null / 空 / 未知均按 ANY（或签）处理
     * @param approverCount 该步骤配置的审批人总数
     * @param approvedCount 该步骤已产生 "通过" 记录的审批人数量
     * @return 步骤是否整体通过
     */
    public static boolean isStepFullyApproved(String approveMode, long approverCount, long approvedCount) {
        if (isAllMode(approveMode)) {
            // 会签：必须全部审批人都通过；未配置审批人视为未通过
            return approverCount > 0 && approvedCount >= approverCount;
        }
        // 或签：至少有一人通过即可
        return approvedCount > 0;
    }

    private static boolean isAllMode(String approveMode) {
        return MODE_ALL.equalsIgnoreCase(approveMode != null ? approveMode.trim() : "");
    }
}
