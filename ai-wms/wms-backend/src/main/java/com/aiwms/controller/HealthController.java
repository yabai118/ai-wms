package com.aiwms.controller;

import com.aiwms.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口（用于验证项目能正常启动）
 */
@Slf4j
@RestController
@RequestMapping("/health")
public class HealthController {

    /** GET /api/health —— 探活 */
    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("service", "wms-backend");
        data.put("time", LocalDateTime.now().toString());
        data.put("javaVersion", System.getProperty("java.version"));
        return Result.success("服务正常", data);
    }

    /** GET /api/health/echo?msg=xxx —— 回显测试 */
    @GetMapping("/echo")
    public Result<String> echo(String msg) {
        log.info("收到回显请求: {}", msg);
        return Result.success("收到: " + msg);
    }
}
