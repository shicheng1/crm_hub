package com.zmd.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zmd.order.entity.ApprovalFlow;
import com.zmd.order.entity.ApprovalFlowStep;
import com.zmd.order.entity.ApprovalStepApprover;
import com.zmd.order.mapper.ApprovalFlowMapper;
import com.zmd.order.mapper.ApprovalFlowStepMapper;
import com.zmd.order.mapper.ApprovalStepApproverMapper;
import com.zmd.order.service.ApprovalFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApprovalFlowServiceImpl implements ApprovalFlowService {

    private final ApprovalFlowMapper flowMapper;
    private final ApprovalFlowStepMapper stepMapper;
    private final ApprovalStepApproverMapper stepApproverMapper;

    @Override
    public List<ApprovalFlow> listFlows() {
        List<ApprovalFlow> flows = flowMapper.selectList(
                new LambdaQueryWrapper<ApprovalFlow>().eq(ApprovalFlow::getStatus, 1));
        if (flows.isEmpty()) return flows;

        // 批量加载所有流程的步骤（避免 N+1）
        List<Long> flowIds = flows.stream().map(ApprovalFlow::getId).collect(Collectors.toList());
        List<ApprovalFlowStep> allSteps = stepMapper.selectList(
                new LambdaQueryWrapper<ApprovalFlowStep>()
                        .in(ApprovalFlowStep::getFlowId, flowIds)
                        .orderByAsc(ApprovalFlowStep::getStepOrder));
        // 批量加载所有步骤的审批人
        Map<Long, List<ApprovalFlowStep>> stepByFlow = allSteps.stream()
                .collect(Collectors.groupingBy(ApprovalFlowStep::getFlowId));
        if (!allSteps.isEmpty()) {
            List<Long> stepIds = allSteps.stream().map(ApprovalFlowStep::getId).collect(Collectors.toList());
            List<ApprovalStepApprover> allApprovers = stepApproverMapper.selectList(
                    new LambdaQueryWrapper<ApprovalStepApprover>()
                            .in(ApprovalStepApprover::getStepId, stepIds));
            Map<Long, List<ApprovalStepApprover>> approverByStep = allApprovers.stream()
                    .collect(Collectors.groupingBy(ApprovalStepApprover::getStepId));
            for (ApprovalFlowStep step : allSteps) {
                step.setApprovers(approverByStep.getOrDefault(step.getId(), java.util.Collections.emptyList()));
            }
        }
        for (ApprovalFlow flow : flows) {
            flow.setSteps(stepByFlow.getOrDefault(flow.getId(), java.util.Collections.emptyList()));
        }
        return flows;
    }

    @Override
    public ApprovalFlow getFlowDetail(Long flowId) {
        ApprovalFlow flow = flowMapper.selectById(flowId);
        if (flow == null) return null;
        flow.setSteps(loadStepsWithApprovers(flowId));
        return flow;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFlow(ApprovalFlow flow) {
        flowMapper.insert(flow);
        if (flow.getSteps() != null) {
            for (ApprovalFlowStep step : flow.getSteps()) {
                step.setFlowId(flow.getId());
                stepMapper.insert(step);
                // 保存步骤审批人
                if (step.getApprovers() != null) {
                    for (ApprovalStepApprover approver : step.getApprovers()) {
                        approver.setStepId(step.getId());
                        stepApproverMapper.insert(approver);
                    }
                }
            }
        }
        return flow.getId();
    }

    private List<ApprovalFlowStep> loadStepsWithApprovers(Long flowId) {
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
                step.setApprovers(approverMap.getOrDefault(step.getId(), java.util.Collections.emptyList()));
            }
        }
        return steps;
    }
}
