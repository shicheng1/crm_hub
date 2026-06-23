package com.zmd.order.controller;

import com.zmd.order.common.R;
import com.zmd.order.entity.ApprovalFlow;
import com.zmd.order.service.ApprovalFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flow")
@RequiredArgsConstructor
public class ApprovalFlowController {

    private final ApprovalFlowService flowService;

    @GetMapping("/list")
    public R<List<ApprovalFlow>> list() {
        return R.ok(flowService.listFlows());
    }

    @GetMapping("/detail/{id}")
    public R<ApprovalFlow> detail(@PathVariable Long id) {
        return R.ok(flowService.getFlowDetail(id));
    }

    @PostMapping("/create")
    public R<Long> create(@RequestBody ApprovalFlow flow) {
        return R.ok(flowService.createFlow(flow));
    }
}
