package com.zmd.order.service;

import com.zmd.order.entity.ApprovalFlow;
import java.util.List;

public interface ApprovalFlowService {
    List<ApprovalFlow> listFlows();
    ApprovalFlow getFlowDetail(Long flowId);
    Long createFlow(ApprovalFlow flow);
}
