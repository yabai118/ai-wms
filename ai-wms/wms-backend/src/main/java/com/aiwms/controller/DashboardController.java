package com.aiwms.controller;

import com.aiwms.common.Result;
import com.aiwms.dto.DashboardVO;
import com.aiwms.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页看板接口
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 看板全部数据（一次请求返回，减少前端请求数）
     * <p>GET /api/dashboard
     */
    @GetMapping
    public Result<DashboardVO> dashboard() {
        return Result.success(dashboardService.getDashboard());
    }
}
