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
        for (ApprovalFlow flow : flows) {
            flow.setSteps(loadStepsWithApprovers(flow.getId()));
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
        for (ApprovalFlowStep step : steps) {
            step.setApprovers(stepApproverMapper.selectList(
                    new LambdaQueryWrapper<ApprovalStepApprover>()
                            .eq(ApprovalStepApprover::getStepId, step.getId())));
        }
        return steps;
    }
}
