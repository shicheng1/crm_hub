package com.zmd.order.controller;

import com.zmd.order.common.R;
import com.zmd.order.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        return R.ok(dashboardService.getStats());
    }

    @GetMapping("/trend")
    public R<Map<String, Object>> trend() {
        return R.ok(dashboardService.getTrend());
    }
}
