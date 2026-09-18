package com.aiwms.controller;

import com.aiwms.common.BusinessException;
import com.aiwms.common.Result;
import com.aiwms.dto.ImportResultVO;
import com.aiwms.service.ImportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 数据导入接口
 *
 * <p>对应真实 WMS 的「数据初始化」——系统部署后是空的，
 * 商品、库位、期初库存由用户通过 Excel 导入。
 */
@RestController
@RequestMapping("/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    /** 支持的导入类型 */
    @GetMapping("/types")
    public Result<List<String>> types() {
        return Result.success(importService.supportedTypes());
    }

    /**
     * 下载导入模板
     * <p>GET /api/import/template/product
     */
    @GetMapping("/template/{type}")
    public void template(@PathVariable String type, HttpServletResponse response) throws IOException {
        String fileName = switch (type) {
            case "product" -> "商品导入模板.xlsx";
            case "location" -> "库位导入模板.xlsx";
            case "inventory" -> "期初库存导入模板.xlsx";
            default -> throw new BusinessException("未知模板类型: " + type);
        };
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        importService.writeTemplate(type, response.getOutputStream());
    }

    /**
     * 导入商品（款 + 尺码）
     * <p>POST /api/import/products
     */
    @PostMapping("/products")
    public Result<ImportResultVO> importProducts(@RequestParam("file") MultipartFile file) {
        ImportResultVO r = importService.importProducts(file);
        return Result.success(summary(r), r);
    }

    /**
     * 导入库位
     * <p>POST /api/import/locations
     */
    @PostMapping("/locations")
    public Result<ImportResultVO> importLocations(@RequestParam("file") MultipartFile file) {
        ImportResultVO r = importService.importLocations(file);
        return Result.success(summary(r), r);
    }

    /**
     * 导入期初库存
     * <p>POST /api/import/inventory
     */
    @PostMapping("/inventory")
    public Result<ImportResultVO> importInventory(@RequestParam("file") MultipartFile file) {
        ImportResultVO r = importService.importInventory(file);
        return Result.success(summary(r), r);
    }

    private String summary(ImportResultVO r) {
        return String.format("导入完成：成功 %d 行，跳过 %d 行，失败 %d 行",
                r.getSuccessRows(), r.getSkippedRows(), r.getFailedRows());
    }
}
