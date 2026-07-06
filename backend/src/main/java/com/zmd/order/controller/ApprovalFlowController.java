package com.zmd.order.controller;

import com.zmd.order.auth.LoginUser;
import com.zmd.order.auth.RequireRole;
import com.zmd.order.common.R;
import com.zmd.order.entity.ApprovalFlow;
import com.zmd.order.service.AuditLogService;
import com.zmd.order.service.ApprovalFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/flow")
@RequiredArgsConstructor
public class ApprovalFlowController {

    private final ApprovalFlowService flowService;
    private final AuditLogService auditLogService;

    @GetMapping("/list")
    public R<List<ApprovalFlow>> list() {
        return R.ok(flowService.listFlows());
    }

    @GetMapping("/detail/{id}")
    public R<ApprovalFlow> detail(@PathVariable Long id) {
        return R.ok(flowService.getFlowDetail(id));
    }

    @PostMapping("/create")
    @RequireRole("ADMIN")
    public R<Long> create(@RequestBody ApprovalFlow flow, HttpServletRequest request) {
        Long id = flowService.createFlow(flow);
        audit("CREATE", "FLOW", id, "创建审批流: " + flow.getName(), request);
        return R.ok(id);
    }

    private void audit(String action, String targetType, Long targetId, String detail, HttpServletRequest request) {
        LoginUser user = LoginUser.get();
        auditLogService.log(
                user != null ? user.getUserId() : null,
                user != null ? user.getUsername() : null,
                action, targetType, targetId, detail, getClientIp(request));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}
