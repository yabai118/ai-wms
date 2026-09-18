package com.aiwms.controller;

import com.aiwms.dto.InventoryQuery;
import com.aiwms.dto.OutboundOrderQuery;
import com.aiwms.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * 数据导出接口
 *
 * <p>与「数据导入」配成一对。筛选条件与各列表页一致，
 * 用户在页面选了什么，导出的就是什么。
 */
@RestController
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    /**
     * 导出库存
     * <p>GET /api/export/inventory?skuCode=8N10W9&onlyAvailable=true
     */
    @GetMapping("/inventory")
    public void exportInventory(InventoryQuery query, HttpServletResponse response) throws IOException {
        prepare(response, "库存明细");
        exportService.exportInventory(query, response.getOutputStream());
    }

    /**
     * 导出出库订单
     * <p>GET /api/export/orders?status=0
     */
    @GetMapping("/orders")
    public void exportOrders(OutboundOrderQuery query, HttpServletResponse response) throws IOException {
        prepare(response, "出库订单");
        exportService.exportOrders(query, response.getOutputStream());
    }

    /**
     * 导出某波次的拣货任务（拣货单）
     * <p>GET /api/export/wave/{id}/tasks
     */
    @GetMapping("/wave/{id}/tasks")
    public void exportPickTasks(@PathVariable Long id, HttpServletResponse response) throws IOException {
        prepare(response, "拣货任务");
        exportService.exportPickTasks(id, response.getOutputStream());
    }

    /** 统一设置下载响应头 */
    private void prepare(HttpServletResponse response, String prefix) {
        String fileName = prefix + "_" + LocalDate.now() + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
    }
}
