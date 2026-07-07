package com.zmd.order.approval.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zmd.order.approval.ApprovalModeEvaluator;
import com.zmd.order.auth.LoginUser;
import com.zmd.order.common.BusinessException;
import com.zmd.order.common.Constants;
import com.zmd.order.dto.ApprovalOutcome;
import com.zmd.order.entity.ApprovalFlow;
import com.zmd.order.entity.ApprovalFlowStep;
import com.zmd.order.entity.ApprovalRecord;
import com.zmd.order.entity.ApprovalStepApprover;
import com.zmd.order.entity.OrderFlowSnapshot;
import com.zmd.order.entity.WorkOrder;
import com.zmd.order.mapper.ApprovalFlowMapper;
import com.zmd.order.mapper.ApprovalFlowStepMapper;
import com.zmd.order.mapper.ApprovalRecordMapper;
import com.zmd.order.mapper.ApprovalStepApproverMapper;
import com.zmd.order.mapper.OrderFlowSnapshotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * 审批引擎：集中承载审批流的「推进 / 驳回 / 跳过 / 权限 / 快照解析 / 状态计算」逻辑。
 *
 * <p>设计原则（高内聚）：
 * <ul>
 *     <li>只计算「下一步应该是什么状态/步骤」，不写数据库、不清缓存、不发通知；</li>
 *     <li>所有副作用（落库、缓存失效、通知）由 OrderServiceImpl 编排、由事件监听器执行；</li>
 *     <li>纯逻辑 + 只读查询，便于独立单元测试。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalEngineService {

    private final ApprovalFlowStepMapper stepMapper;
    private final ApprovalStepApproverMapper stepApproverMapper;
    private final ApprovalRecordMapper recordMapper;
    private final ApprovalFlowMapper flowMapper;
    private final OrderFlowSnapshotMapper flowSnapshotMapper;
    private final ObjectMapper objectMapper;

    // ==================== 对外：审批推进 / 驳回（只算状态） ====================

    public ApprovalOutcome approve(WorkOrder order, ApprovalFlowStep currentStep, LoginUser loginUser) {
        String approveMode = currentStep.getApproveMode() != null ? currentStep.getApproveMode() : ApprovalModeEvaluator.MODE_ANY;
        if (ApprovalModeEvaluator.MODE_ALL.equalsIgnoreCase(approveMode) && !isAllApproversApproved(order.getId(), currentStep)) {
            // 会签模式：当前审批人通过，但还有其他人未通过，流程停留在当前步等待
            return new ApprovalOutcome(Constants.STATUS_REVIEWING, currentStep.getStepOrder(), false,
                    "APPROVE", "通过 [" + currentStep.getStepName() + "]，会签模式，等待其他审批人处理", false);
        }

        ApprovalFlowStep nextStep = getNextStep(order, currentStep.getStepOrder());
        if (nextStep == null) {
            // 最后一步通过 → 工单完成
            return new ApprovalOutcome(Constants.STATUS_APPROVED, null, true,
                    "APPROVE", "通过 [" + currentStep.getStepName() + "]，审批全部完成", true);
        }

        // 跳过逻辑：下一步审批人与当前审批人相同则自动跳过
        nextStep = skipDuplicateApprovers(order, nextStep.getStepOrder(), loginUser.getUserId());
        if (nextStep == null) {
            return new ApprovalOutcome(Constants.STATUS_APPROVED, null, true,
                    "APPROVE", "通过 [" + currentStep.getStepName() + "]，后续步骤审批人相同自动跳过，审批完成", true);
        }

        // 流转到下一步
        return new ApprovalOutcome(Constants.STATUS_REVIEWING, nextStep.getStepOrder(), false,
                "APPROVE", "通过 [" + currentStep.getStepName() + "]，流转至 [" + nextStep.getStepName() + "]", false);
    }

    public ApprovalOutcome reject(WorkOrder order, ApprovalFlowStep currentStep, LoginUser loginUser, String remark) {
        String rejectMode = getRejectMode(order);
        int targetStatus;
        Integer targetStep;
        String logDetail;
        switch (rejectMode) {
            case "RESTART":
                targetStatus = Constants.STATUS_REVIEWING;
                targetStep = 1;
                logDetail = "驳回 [" + currentStep.getStepName() + "]，策略=RESTART，回到第一步重新审批";
                break;
            case "PREVIOUS":
                ApprovalFlowStep prevStep = getPrevStep(order, currentStep.getStepOrder());
                if (prevStep != null) {
                    targetStatus = Constants.STATUS_REVIEWING;
                    targetStep = prevStep.getStepOrder();
                    logDetail = "驳回 [" + currentStep.getStepName() + "]，策略=PREVIOUS，退回 [" + prevStep.getStepName() + "]";
                } else {
                    targetStatus = Constants.STATUS_RETURNED;
                    targetStep = 0;
                    logDetail = "驳回 [" + currentStep.getStepName() + "]，策略=PREVIOUS，无上一步，退回发起人";
                }
                break;
            case "ORIGIN":
            default:
                targetStatus = Constants.STATUS_RETURNED;
                targetStep = 0;
                logDetail = "驳回 [" + currentStep.getStepName() + "]，策略=ORIGIN，退回发起人重新提交";
                break;
        }
        return new ApprovalOutcome(targetStatus, targetStep, false, "REJECT", logDetail, true);
    }

    // ==================== 对外：工单服务编排所需的流程查询 ====================

    /** 创建工单时保存审批流快照，防止流程后续修改影响历史工单 */
    public void createFlowSnapshot(Long orderId, Long flowId) {
        ApprovalFlow flow = flowMapper.selectById(flowId);
        if (flow == null) {
            throw new BusinessException("审批流不存在");
        }
        flow.setSteps(getRuntimeSteps(flowId));
        OrderFlowSnapshot snapshot = new OrderFlowSnapshot();
        snapshot.setOrderId(orderId);
        snapshot.setFlowId(flowId);
        try {
            snapshot.setSnapshotJson(objectMapper.writeValueAsString(flow));
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "审批流快照创建失败");
        }
        snapshot.setCreateTime(LocalDateTime.now());
        flowSnapshotMapper.insert(snapshot);
    }

    public ApprovalFlow getFlowSnapshot(WorkOrder order) {
        OrderFlowSnapshot snapshot = flowSnapshotMapper.selectOne(
                new LambdaQueryWrapper<OrderFlowSnapshot>()
                        .eq(OrderFlowSnapshot::getOrderId, order.getId()));
        if (snapshot == null || snapshot.getSnapshotJson() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(snapshot.getSnapshotJson(), ApprovalFlow.class);
        } catch (Exception e) {
            log.warn("审批流快照反序列化失败, orderId={}", order.getId(), e);
            return null;
        }
    }

    /** 批量查询工单流程快照（按 orderId），供列表 enrich 批量填充，消灭逐条 N+1（P0-2） */
    public Map<Long, ApprovalFlow> getFlowSnapshots(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return Collections.emptyMap();
        List<OrderFlowSnapshot> snapshots = flowSnapshotMapper.selectList(
                new LambdaQueryWrapper<OrderFlowSnapshot>().in(OrderFlowSnapshot::getOrderId, orderIds));
        Map<Long, ApprovalFlow> result = new HashMap<>();
        for (OrderFlowSnapshot s : snapshots) {
            if (s.getSnapshotJson() == null) continue;
            try {
                result.put(s.getOrderId(), objectMapper.readValue(s.getSnapshotJson(), ApprovalFlow.class));
            } catch (Exception e) {
                log.warn("审批流快照反序列化失败, orderId={}", s.getOrderId(), e);
            }
        }
        return result;
    }

    public ApprovalFlow getRuntimeFlow(Long flowId) {
        return flowMapper.selectById(flowId);
    }

    public List<ApprovalFlowStep> getRuntimeSteps(Long flowId) {
        return loadRuntimeStepsWithApprovers(flowId);
    }

    public String getRejectMode(WorkOrder order) {
        ApprovalFlow snapshot = getFlowSnapshot(order);
        if (snapshot != null && snapshot.getRejectMode() != null) {
            return snapshot.getRejectMode();
        }
        ApprovalFlow flow = flowMapper.selectById(order.getFlowId());
        return flow != null && flow.getRejectMode() != null ? flow.getRejectMode() : "ORIGIN";
    }

    public ApprovalFlowStep getStep(WorkOrder order, int stepOrder) {
        List<ApprovalFlowStep> snapshotSteps = getSnapshotSteps(order);
        if (snapshotSteps != null) {
            return snapshotSteps.stream()
                    .filter(s -> s.getStepOrder() != null && s.getStepOrder() == stepOrder)
                    .findFirst()
                    .orElse(null);
        }
        return stepMapper.selectOne(
                new LambdaQueryWrapper<ApprovalFlowStep>()
                        .eq(ApprovalFlowStep::getFlowId, order.getFlowId())
                        .eq(ApprovalFlowStep::getStepOrder, stepOrder));
    }

    /** 判断某人是否是当前步骤的审批人（兼容快照和运行时配置） */
    public boolean isStepApprover(ApprovalFlowStep step, Long userId) {
        if (step.getApprovers() != null) {
            return step.getApprovers().stream().anyMatch(a -> userId.equals(a.getUserId()));
        }
        return stepApproverMapper.selectCount(
                new LambdaQueryWrapper<ApprovalStepApprover>()
                        .eq(ApprovalStepApprover::getStepId, step.getId())
                        .eq(ApprovalStepApprover::getUserId, userId)) > 0;
    }

    /** 判断某人是否已审批过该步骤 */
    public boolean hasApproved(Long orderId, Long stepId, Long userId) {
        return recordMapper.selectCount(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getOrderId, orderId)
                        .eq(ApprovalRecord::getStepId, stepId)
                        .eq(ApprovalRecord::getApproverId, userId)) > 0;
    }

    // ==================== 内部工具方法 ====================

    private List<ApprovalFlowStep> getSnapshotSteps(WorkOrder order) {
        ApprovalFlow snapshot = getFlowSnapshot(order);
        return snapshot != null && snapshot.getSteps() != null ? snapshot.getSteps() : null;
    }

    private List<ApprovalFlowStep> loadRuntimeStepsWithApprovers(Long flowId) {
        List<ApprovalFlowStep> steps = stepMapper.selectList(
                new LambdaQueryWrapper<ApprovalFlowStep>()
                        .eq(ApprovalFlowStep::getFlowId, flowId)
                        .orderByAsc(ApprovalFlowStep::getStepOrder));
        if (!steps.isEmpty()) {
            List<Long> stepIds = steps.stream().map(ApprovalFlowStep::getId).collect(Collectors.toList());
            List<ApprovalStepApprover> allApprovers = stepApproverMapper.selectList(
                    new LambdaQueryWrapper<ApprovalStepApprover>()
                            .in(ApprovalStepApprover::getStepId, stepIds));
            Map<Long, List<ApprovalStepApprover>> approverMap = allApprovers.stream()
                    .collect(Collectors.groupingBy(ApprovalStepApprover::getStepId));
            for (ApprovalFlowStep step : steps) {
                step.setApprovers(approverMap.getOrDefault(step.getId(), new ArrayList<>()));
            }
        }
        return steps;
    }

    /** 会签模式：判断当前步骤所有审批人是否都已通过 */
    private boolean isAllApproversApproved(Long orderId, ApprovalFlowStep step) {
        long approverCount;
        if (step.getApprovers() != null) {
            approverCount = step.getApprovers().size();
        } else {
            approverCount = stepApproverMapper.selectCount(
                    new LambdaQueryWrapper<ApprovalStepApprover>()
                            .eq(ApprovalStepApprover::getStepId, step.getId()));
        }
        long approvedCount = recordMapper.selectCount(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getOrderId, orderId)
                        .eq(ApprovalRecord::getStepId, step.getId())
                        .eq(ApprovalRecord::getResult, "APPROVED"));
        String approveMode = step.getApproveMode() != null ? step.getApproveMode() : ApprovalModeEvaluator.MODE_ANY;
        return ApprovalModeEvaluator.isStepFullyApproved(approveMode, approverCount, approvedCount);
    }

    /** 跳过审批人相同的连续步骤 */
    private ApprovalFlowStep skipDuplicateApprovers(WorkOrder order, int fromStepOrder, Long approverId) {
        ApprovalFlowStep step = getStep(order, fromStepOrder);
        while (step != null && isStepApprover(step, approverId) && !hasApproved(order.getId(), step.getId(), approverId)) {
            log.info("跳过审批人相同的步骤: orderId={}, flowId={}, stepOrder={}, approverId={}", order.getId(), order.getFlowId(), step.getStepOrder(), approverId);
            step = getNextStep(order, step.getStepOrder());
        }
        return step;
    }

    private ApprovalFlowStep getNextStep(WorkOrder order, int currentStepOrder) {
        List<ApprovalFlowStep> snapshotSteps = getSnapshotSteps(order);
        if (snapshotSteps != null) {
            return snapshotSteps.stream()
                    .filter(s -> s.getStepOrder() != null && s.getStepOrder() > currentStepOrder)
                    .min(java.util.Comparator.comparing(ApprovalFlowStep::getStepOrder))
                    .orElse(null);
        }
        return stepMapper.selectOne(
                new LambdaQueryWrapper<ApprovalFlowStep>()
                        .eq(ApprovalFlowStep::getFlowId, order.getFlowId())
                        .gt(ApprovalFlowStep::getStepOrder, currentStepOrder)
                        .orderByAsc(ApprovalFlowStep::getStepOrder)
                        .last("LIMIT 1"));
    }

    private ApprovalFlowStep getPrevStep(WorkOrder order, int currentStepOrder) {
        List<ApprovalFlowStep> snapshotSteps = getSnapshotSteps(order);
        if (snapshotSteps != null) {
            return snapshotSteps.stream()
                    .filter(s -> s.getStepOrder() != null && s.getStepOrder() < currentStepOrder)
                    .max(java.util.Comparator.comparing(ApprovalFlowStep::getStepOrder))
                    .orElse(null);
        }
        return stepMapper.selectOne(
                new LambdaQueryWrapper<ApprovalFlowStep>()
                        .eq(ApprovalFlowStep::getFlowId, order.getFlowId())
                        .lt(ApprovalFlowStep::getStepOrder, currentStepOrder)
                        .orderByDesc(ApprovalFlowStep::getStepOrder)
                        .last("LIMIT 1"));
    }
}
